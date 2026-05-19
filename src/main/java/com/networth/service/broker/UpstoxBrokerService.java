package com.networth.service.broker;

import com.networth.model.dto.HoldingRequest;
import com.networth.model.entity.BrokerConnection;
import com.networth.model.entity.DematAccount;
import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.repository.BrokerConnectionRepository;
import com.networth.repository.DematAccountRepository;
import com.networth.repository.HoldingRepository;
import com.networth.service.DematAccountService;
import com.networth.service.portfolio.HoldingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpstoxBrokerService {

    private final RestTemplate restTemplate;
    private final BrokerConnectionRepository connectionRepository;
    private final DematAccountRepository dematAccountRepository;
    private final HoldingRepository holdingRepository;
    private final HoldingService holdingService;

    private static final String BROKER_NAME = "UPSTOX";
    private static final String AUTH_URL = "https://api.upstox.com/v2/login/authorization/dialog";
    private static final String TOKEN_URL = "https://api.upstox.com/v2/login/authorization/token";
    private static final String HOLDINGS_URL = "https://api.upstox.com/v2/portfolio/long-term-holdings";
    private static final String PROFILE_URL = "https://api.upstox.com/v2/user/profile";

    @Value("${broker.upstox.client-id:}")
    private String clientId;

    @Value("${broker.upstox.client-secret:}")
    private String clientSecret;

    @Value("${broker.upstox.redirect-uri:http://localhost:3000/broker/upstox/callback}")
    private String redirectUri;

    public String getAuthUrl(UUID userId) {
        return AUTH_URL
                + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&state=" + userId.toString();
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> exchangeCodeForToken(UUID userId, String code) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("code", code);
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("redirect_uri", redirectUri);
            body.add("grant_type", "authorization_code");

            ResponseEntity<Map> response = restTemplate.exchange(TOKEN_URL, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Map.of("success", false, "message", "Token exchange failed");
            }

            String accessToken = (String) response.getBody().get("access_token");
            if (accessToken == null) {
                return Map.of("success", false, "message", "No access token in response");
            }

            // Fetch user profile
            String brokerUserId = null;
            String brokerUserName = null;
            try {
                HttpHeaders profileHeaders = new HttpHeaders();
                profileHeaders.set("Authorization", "Bearer " + accessToken);
                profileHeaders.set("Accept", "application/json");
                ResponseEntity<Map> profileResp = restTemplate.exchange(PROFILE_URL, HttpMethod.GET,
                        new HttpEntity<>(profileHeaders), Map.class);
                if (profileResp.getStatusCode().is2xxSuccessful() && profileResp.getBody() != null) {
                    Map<String, Object> data = (Map<String, Object>) profileResp.getBody().get("data");
                    if (data != null) {
                        brokerUserId = (String) data.get("user_id");
                        brokerUserName = (String) data.get("user_name");
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch Upstox profile: {}", e.getMessage());
            }

            // Save or update connection
            BrokerConnection conn = connectionRepository.findByUserIdAndBroker(userId, BROKER_NAME)
                    .orElse(BrokerConnection.builder().userId(userId).broker(BROKER_NAME).build());
            conn.setAccessToken(accessToken);
            conn.setStatus("ACTIVE");
            conn.setBrokerUserId(brokerUserId);
            conn.setBrokerUserName(brokerUserName);
            connectionRepository.save(conn);

            log.info("Upstox connected for user {} (broker user: {})", userId, brokerUserName);
            return Map.of("success", true, "message", "Upstox connected",
                    "brokerUserName", brokerUserName != null ? brokerUserName : "");
        } catch (Exception e) {
            log.error("Upstox token exchange failed: {}", e.getMessage());
            return Map.of("success", false, "message", "Failed: " + e.getMessage());
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> syncHoldings(UUID userId) {
        BrokerConnection conn = connectionRepository.findByUserIdAndBroker(userId, BROKER_NAME)
                .orElseThrow(() -> new IllegalStateException("Upstox not connected"));

        if (!"ACTIVE".equals(conn.getStatus())) {
            return Map.of("success", false, "message", "Upstox connection is not active. Please reconnect.");
        }

        try {
            // Fetch holdings from Upstox
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + conn.getAccessToken());
            headers.set("Accept", "application/json");

            ResponseEntity<Map> response = restTemplate.exchange(HOLDINGS_URL, HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                conn.setStatus("TOKEN_EXPIRED");
                connectionRepository.save(conn);
                return Map.of("success", false, "message", "Failed to fetch holdings. Token may be expired.");
            }

            Map<String, Object> responseBody = response.getBody();
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            if (data == null) {
                return Map.of("success", false, "message", "No data in response");
            }

            List<Map<String, Object>> upstoxHoldings = (List<Map<String, Object>>) data.get("holdings");
            if (upstoxHoldings == null || upstoxHoldings.isEmpty()) {
                return Map.of("success", true, "message", "No holdings found in Upstox account",
                        "created", 0, "updated", 0);
            }

            // Create/find demat account
            DematAccount demat = getOrCreateDematAccount(userId, conn);
            conn.setDematAccountId(demat.getId());

            int created = 0;
            int updated = 0;
            int skipped = 0;

            for (Map<String, Object> uh : upstoxHoldings) {
                try {
                    String tradingSymbol = (String) uh.get("tradingsymbol");
                    String isin = (String) uh.get("isin");
                    String companyName = (String) uh.get("company_name");
                    String exchange = (String) uh.get("exchange");
                    Number quantity = (Number) uh.get("quantity");
                    Number avgPrice = (Number) uh.get("average_price");
                    Number lastPrice = (Number) uh.get("last_price");
                    Number pnl = (Number) uh.get("pnl");

                    if (tradingSymbol == null || quantity == null || avgPrice == null) {
                        skipped++;
                        continue;
                    }

                    String symbol = tradingSymbol + ".NS";
                    BigDecimal qty = BigDecimal.valueOf(quantity.doubleValue());
                    BigDecimal avg = BigDecimal.valueOf(avgPrice.doubleValue());

                    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                        skipped++;
                        continue;
                    }

                    // Exact match by user + symbol + demat account (prevents duplicates on re-sync)
                    Optional<Holding> existingOpt = holdingRepository
                            .findByUserIdAndSymbolAndDematAccountId(userId, symbol, demat.getId());
                    Holding match = existingOpt.orElse(null);

                    if (match != null) {
                        // Update existing
                        match.setQuantity(qty);
                        match.setAverageBuyPrice(avg);
                        if (isin != null) match.setIsin(isin);
                        if (lastPrice != null) {
                            match.setCurrentPrice(BigDecimal.valueOf(lastPrice.doubleValue()));
                            match.setCurrentValue(qty.multiply(match.getCurrentPrice()));
                        }
                        if (companyName != null) match.setName(companyName);
                        holdingRepository.save(match);
                        updated++;
                    } else {
                        // Create new
                        HoldingRequest req = HoldingRequest.builder()
                                .assetType(AssetType.EQUITY)
                                .symbol(symbol)
                                .name(companyName != null ? companyName : tradingSymbol)
                                .quantity(qty)
                                .averageBuyPrice(avg)
                                .exchange(exchange)
                                .isin(isin)
                                .dematAccountId(demat.getId().toString())
                                .build();
                        holdingService.createHolding(userId.toString(), req);
                        created++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to sync holding: {}", e.getMessage());
                    skipped++;
                }
            }

            conn.setLastSyncedAt(LocalDateTime.now());
            connectionRepository.save(conn);

            log.info("Upstox sync for user {}: {} created, {} updated, {} skipped",
                    userId, created, updated, skipped);
            return Map.of("success", true,
                    "message", String.format("%d created, %d updated, %d skipped", created, updated, skipped),
                    "created", created, "updated", updated, "skipped", skipped,
                    "total", upstoxHoldings.size());
        } catch (Exception e) {
            log.error("Upstox sync failed for user {}: {}", userId, e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                conn.setStatus("TOKEN_EXPIRED");
                connectionRepository.save(conn);
            }
            return Map.of("success", false, "message", "Sync failed: " + e.getMessage());
        }
    }

    public Map<String, Object> getConnectionStatus(UUID userId) {
        Optional<BrokerConnection> conn = connectionRepository.findByUserIdAndBroker(userId, BROKER_NAME);
        if (conn.isEmpty()) {
            return Map.of("connected", false);
        }
        BrokerConnection c = conn.get();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("connected", true);
        status.put("status", c.getStatus());
        status.put("brokerUserName", c.getBrokerUserName());
        status.put("lastSyncedAt", c.getLastSyncedAt());
        return status;
    }

    @Transactional
    public void disconnect(UUID userId) {
        connectionRepository.findByUserIdAndBroker(userId, BROKER_NAME)
                .ifPresent(conn -> {
                    conn.setStatus("DISCONNECTED");
                    conn.setAccessToken(null);
                    connectionRepository.save(conn);
                });
    }

    private DematAccount getOrCreateDematAccount(UUID userId, BrokerConnection conn) {
        // Check if demat already linked
        if (conn.getDematAccountId() != null) {
            Optional<DematAccount> existing = dematAccountRepository.findById(conn.getDematAccountId());
            if (existing.isPresent()) return existing.get();
        }

        // Check if user already has an Upstox demat
        List<DematAccount> demats = dematAccountRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Optional<DematAccount> upstoxDemat = demats.stream()
                .filter(d -> "Upstox".equalsIgnoreCase(d.getBrokerName()))
                .findFirst();
        if (upstoxDemat.isPresent()) return upstoxDemat.get();

        // Create new
        DematAccount demat = DematAccount.builder()
                .userId(userId)
                .brokerName("Upstox")
                .accountNumber(conn.getBrokerUserId())
                .accountType("Equity")
                .description("Auto-created from Upstox import")
                .isDefault(demats.isEmpty())
                .build();
        return dematAccountRepository.save(demat);
    }
}
