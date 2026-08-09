package com.networth.service.market;

import com.networth.model.entity.NpsAccount;
import com.networth.repository.NpsAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Fetches NPS NAV data from npsnav.in (free, no auth required).
 *
 * API endpoints used:
 * - GET /api/schemes             — all scheme codes + names
 * - GET /api/{scheme_code}       — latest NAV as plain text
 * - GET /api/latest-min          — all NAVs in bulk (scheme_code, nav)
 * - GET /api/detailed/{code}     — detailed with returns
 * - GET /api/historical/{code}   — historical daily NAVs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NpsNavService {

    private final RestTemplate restTemplate;
    private final NpsAccountRepository npsAccountRepository;

    @Value("${nps.nav.base-url:https://npsnav.in/api}")
    private String baseUrl;

    private static final DateTimeFormatter NAV_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // In-memory cache of all schemes (refreshed on startup + daily)
    private volatile List<Map<String, String>> schemesCache = Collections.emptyList();
    private volatile Map<String, BigDecimal> navCache = Collections.emptyMap();
    private volatile String navCacheDate = null;

    /**
     * Get all NPS schemes (cached in memory).
     * Returns list of {schemeCode, schemeName}.
     */
    public List<Map<String, String>> getSchemes() {
        if (schemesCache.isEmpty()) {
            refreshSchemes();
        }
        return schemesCache;
    }

    /**
     * Get latest NAV for a specific scheme.
     */
    public BigDecimal getLatestNav(String schemeCode) {
        if (schemeCode == null || schemeCode.isBlank()) return null;

        // Try cache first
        BigDecimal cached = navCache.get(schemeCode);
        if (cached != null) return cached;

        // Fetch single scheme NAV
        try {
            String navStr = restTemplate.getForObject(baseUrl + "/" + schemeCode, String.class);
            if (navStr != null && !navStr.isBlank()) {
                return new BigDecimal(navStr.trim());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch NPS NAV for {}: {}", schemeCode, e.getMessage());
        }
        return null;
    }

    /**
     * Get detailed fund data (NAV + returns) for a scheme.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDetailedScheme(String schemeCode) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    baseUrl + "/detailed/" + schemeCode, Map.class);
            return response;
        } catch (Exception e) {
            log.warn("Failed to fetch NPS detailed data for {}: {}", schemeCode, e.getMessage());
            return null;
        }
    }

    /**
     * Get historical NAV data for a scheme.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getHistoricalNav(String schemeCode) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    baseUrl + "/historical/" + schemeCode, Map.class);
            if (response != null && response.containsKey("data")) {
                return (List<Map<String, Object>>) response.get("data");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch NPS historical data for {}: {}", schemeCode, e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Refresh all NPS account values for a specific user.
     * For each account with a scheme_code + units, fetch latest NAV and compute currentValue.
     */
    @Transactional
    public Map<String, Object> refreshUserNpsValues(UUID userId) {
        List<NpsAccount> accounts = npsAccountRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int updated = 0;
        int skipped = 0;

        for (NpsAccount acc : accounts) {
            if (acc.getSchemeCode() == null || acc.getSchemeCode().isBlank()) {
                skipped++;
                continue;
            }

            BigDecimal nav = getLatestNav(acc.getSchemeCode());
            if (nav == null) {
                skipped++;
                continue;
            }

            acc.setNav(nav);
            acc.setNavDate(LocalDate.now());

            if (acc.getUnits() != null && acc.getUnits().compareTo(BigDecimal.ZERO) > 0) {
                acc.setCurrentValue(acc.getUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP));
            }

            npsAccountRepository.save(acc);
            updated++;
        }

        log.info("NPS NAV refresh for user {}: {} updated, {} skipped", userId, updated, skipped);
        return Map.of("success", true, "updated", updated, "skipped", skipped);
    }

    /**
     * Bulk refresh all NPS accounts across all users.
     * Called by scheduled job.
     */
    @Transactional
    public void refreshAllNpsValues() {
        // Fetch all NAVs in bulk
        refreshBulkNavCache();

        if (navCache.isEmpty()) {
            log.warn("NPS bulk NAV cache is empty, skipping refresh");
            return;
        }

        List<NpsAccount> allAccounts = npsAccountRepository.findAll();
        int updated = 0;

        for (NpsAccount acc : allAccounts) {
            if (acc.getSchemeCode() == null || acc.getSchemeCode().isBlank()) continue;

            BigDecimal nav = navCache.get(acc.getSchemeCode());
            if (nav == null) continue;

            acc.setNav(nav);
            acc.setNavDate(LocalDate.now());

            if (acc.getUnits() != null && acc.getUnits().compareTo(BigDecimal.ZERO) > 0) {
                acc.setCurrentValue(acc.getUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP));
            }

            npsAccountRepository.save(acc);
            updated++;
        }

        log.info("NPS bulk NAV refresh: {} accounts updated out of {}", updated, allAccounts.size());
    }

    /**
     * Refresh the schemes list from npsnav.in.
     */
    @SuppressWarnings("unchecked")
    public void refreshSchemes() {
        try {
            Map<String, Object> response = restTemplate.getForObject(baseUrl + "/schemes", Map.class);
            if (response != null && response.containsKey("data")) {
                List<List<String>> data = (List<List<String>>) response.get("data");
                List<Map<String, String>> schemes = new ArrayList<>();
                for (List<String> item : data) {
                    if (item.size() >= 2) {
                        Map<String, String> scheme = new HashMap<>();
                        scheme.put("schemeCode", item.get(0));
                        scheme.put("schemeName", item.get(1));
                        schemes.add(scheme);
                    }
                }
                schemesCache = schemes;
                log.info("NPS schemes cache refreshed: {} schemes", schemes.size());
            }
        } catch (Exception e) {
            log.warn("Failed to refresh NPS schemes: {}", e.getMessage());
        }
    }

    /**
     * Refresh the bulk NAV cache from /api/latest-min.
     */
    @SuppressWarnings("unchecked")
    private void refreshBulkNavCache() {
        try {
            Map<String, Object> response = restTemplate.getForObject(baseUrl + "/latest-min", Map.class);
            if (response != null && response.containsKey("data")) {
                List<List<Object>> data = (List<List<Object>>) response.get("data");
                Map<String, BigDecimal> cache = new HashMap<>();
                for (List<Object> item : data) {
                    if (item.size() >= 2 && item.get(0) instanceof String && item.get(1) instanceof Number) {
                        cache.put((String) item.get(0), BigDecimal.valueOf(((Number) item.get(1)).doubleValue()));
                    }
                }
                navCache = cache;
                Map<String, Object> metadata = (Map<String, Object>) response.get("metadata");
                if (metadata != null && metadata.containsKey("lastUpdated")) {
                    navCacheDate = metadata.get("lastUpdated").toString();
                }
                log.info("NPS NAV cache refreshed: {} schemes, date: {}", cache.size(), navCacheDate);
            }
        } catch (Exception e) {
            log.warn("Failed to refresh NPS NAV cache: {}", e.getMessage());
        }
    }

    /**
     * Daily scheduled refresh at 9 PM IST (3:30 PM UTC).
     * NPS NAVs are typically published by evening.
     */
    @Scheduled(cron = "0 30 15 * * ?", zone = "Asia/Kolkata")
    public void scheduledNpsNavRefresh() {
        log.info("Starting scheduled NPS NAV refresh");
        refreshSchemes();
        refreshAllNpsValues();
    }
}
