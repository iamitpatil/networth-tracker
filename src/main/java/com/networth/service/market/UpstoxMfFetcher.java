package com.networth.service.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Fetches mutual fund NAVs from Upstox's MF instruments file.
 *
 * Upstox provides a single JSON file containing ALL mutual funds with their
 * latest NAV (last_price). More efficient than per-fund API calls.
 *
 * File downloaded once and cached in memory. Refreshed daily at 7 AM.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpstoxMfFetcher {

    private static final String MF_INSTRUMENTS_URL =
            "https://assets.upstox.com/market-quote/instruments/exchange/mf-instruments.json.gz";

    @Value("${market.data.upstox.access-token:}")
    private String accessToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, BigDecimal> navCache = new ConcurrentHashMap<>();
    private LocalDate lastRefreshDate;

    public BigDecimal getNav(String isin) {
        if (accessToken == null || accessToken.isBlank()) return null;
        if (isin == null || isin.isBlank()) return null;

        if (navCache.isEmpty() || isStale()) {
            refreshMfData();
        }

        BigDecimal nav = navCache.get(isin);
        if (nav != null) {
            log.debug("Upstox NAV for {}: {}", isin, nav);
        }
        return nav;
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Kolkata")
    public void scheduledRefresh() {
        log.info("Scheduled MF instruments refresh");
        refreshMfData();
    }

    private synchronized void refreshMfData() {
        if (accessToken == null || accessToken.isBlank()) return;

        if (lastRefreshDate != null && lastRefreshDate.equals(LocalDate.now(MarketCalendar.ZONE)) && !navCache.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MF_INSTRUMENTS_URL))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                log.error("Failed to download MF instruments: status {}", response.statusCode());
                return;
            }

            Map<String, BigDecimal> newCache = new HashMap<>();
            try (GZIPInputStream gzip = new GZIPInputStream(response.body())) {
                JsonNode root = objectMapper.readTree(gzip);
                if (root.isArray()) {
                    for (JsonNode mf : root) {
                        String isin = mf.path("instrument_key").asText(null);
                        JsonNode priceNode = mf.path("last_price");
                        if (isin != null && !isin.isBlank() && priceNode.isNumber()) {
                            BigDecimal nav = BigDecimal.valueOf(priceNode.asDouble());
                            if (nav.compareTo(BigDecimal.ZERO) > 0) {
                                newCache.put(isin, nav);
                            }
                        }
                    }
                }
            }

            navCache.clear();
            navCache.putAll(newCache);
            lastRefreshDate = LocalDate.now(MarketCalendar.ZONE);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Upstox MF data refreshed: {} funds loaded in {} ms", navCache.size(), duration);
        } catch (Exception e) {
            log.error("Failed to refresh Upstox MF data: {}", e.getMessage(), e);
        }
    }

    private boolean isStale() {
        return lastRefreshDate == null || !lastRefreshDate.equals(LocalDate.now(MarketCalendar.ZONE));
    }

    public int getCachedFundCount() {
        return navCache.size();
    }

    public boolean isAvailable() {
        return accessToken != null && !accessToken.isBlank() && !navCache.isEmpty();
    }
}
