package com.networth.service.broker;

import com.networth.model.dto.HoldingRequest;
import com.networth.model.entity.BrokerConnection;
import com.networth.model.entity.DematAccount;
import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.BrokerConnectionRepository;
import com.networth.repository.DematAccountRepository;
import com.networth.repository.HoldingRepository;
import com.networth.repository.TransactionRepository;
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
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZerodhaIntegrationService {

    private final RestTemplate restTemplate;
    private final BrokerConnectionRepository connectionRepository;
    private final DematAccountRepository dematAccountRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final HoldingService holdingService;

    private static final String BROKER_NAME = "ZERODHA";
    private static final String BROKER_LABEL = "Zerodha";
    private static final String KITE_LOGIN_URL = "https://kite.zerodha.com/connect/login";
    private static final String KITE_BASE_URL = "https://api.kite.trade";
    private static final String SESSION_URL = KITE_BASE_URL + "/session/token";
    private static final String HOLDINGS_URL = KITE_BASE_URL + "/portfolio/holdings";
    private static final String PROFILE_URL = KITE_BASE_URL + "/user/profile";

    @Value("${broker.zerodha.api-key:}")
    private String apiKey;

    @Value("${broker.zerodha.api-secret:}")
    private String apiSecret;

    @Value("${broker.zerodha.redirect-uri:http://localhost:3000/holdings}")
    private String redirectUri;

    /**
     * Generate Kite Connect login URL for OAuth.
     * User is redirected here to authorize access.
     */
    public String getAuthUrl(UUID userId) {
        return KITE_LOGIN_URL
                + "?v=3"
                + "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
    }

    /**
     * Exchange Kite request_token for an access_token (session token).
     * Kite Connect uses: POST /session/token with api_key, request_token, checksum (SHA-256 of api_key + request_token + api_secret).
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> exchangeRequestToken(UUID userId, String requestToken) {
        try {
            // Compute checksum: SHA-256(api_key + request_token + api_secret)
            String checksum = sha256(apiKey + requestToken + apiSecret);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("X-Kite-Version", "3");

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("api_key", apiKey);
            body.add("request_token", requestToken);
            body.add("checksum", checksum);

            ResponseEntity<Map> response = restTemplate.exchange(SESSION_URL, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Map.of("success", false, "message", "Token exchange failed");
            }

            Map<String, Object> respBody = response.getBody();
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            if (data == null) {
                return Map.of("success", false, "message", "No data in token response");
            }

            String accessToken = (String) data.get("access_token");
            if (accessToken == null) {
                return Map.of("success", false, "message", "No access token in response");
            }

            // Extract user info from session response
            String brokerUserId = data.get("user_id") != null ? data.get("user_id").toString() : null;
            String brokerUserName = data.get("user_name") != null ? data.get("user_name").toString() : null;

            // If user_name not in session response, try fetching profile
            if (brokerUserName == null) {
                try {
                    Map<String, Object> profile = fetchProfile(accessToken);
                    if (profile != null) {
                        brokerUserId = profile.get("user_id") != null ? profile.get("user_id").toString() : brokerUserId;
                        brokerUserName = profile.get("user_name") != null ? profile.get("user_name").toString() : null;
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch Zerodha profile: {}", e.getMessage());
                }
            }

            // Save or update connection
            BrokerConnection conn = connectionRepository.findByUserIdAndBroker(userId, BROKER_NAME)
                    .orElse(BrokerConnection.builder().userId(userId).broker(BROKER_NAME).build());
            conn.setAccessToken(accessToken);
            conn.setStatus("ACTIVE");
            conn.setBrokerUserId(brokerUserId);
            conn.setBrokerUserName(brokerUserName);
            connectionRepository.save(conn);

            log.info("Zerodha connected for user {} (broker user: {})", userId, brokerUserName);
            return Map.of("success", true, "message", "Zerodha connected",
                    "brokerUserName", brokerUserName != null ? brokerUserName : "");
        } catch (Exception e) {
            log.error("Zerodha token exchange failed: {}", e.getMessage());
            return Map.of("success", false, "message", "Failed: " + e.getMessage());
        }
    }

    /**
     * Sync holdings from Zerodha Kite Connect into the app.
     * Uses per-user access token from BrokerConnection.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> syncHoldings(UUID userId) {
        BrokerConnection conn = connectionRepository.findByUserIdAndBroker(userId, BROKER_NAME)
                .orElseThrow(() -> new IllegalStateException("Zerodha not connected"));

        if (!"ACTIVE".equals(conn.getStatus())) {
            return Map.of("success", false, "message", "Zerodha connection is not active. Please reconnect.");
        }

        try {
            HttpHeaders headers = buildAuthHeaders(conn.getAccessToken());

            ResponseEntity<Map> response = restTemplate.exchange(HOLDINGS_URL, HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                conn.setStatus("TOKEN_EXPIRED");
                connectionRepository.save(conn);
                return Map.of("success", false, "message", "Failed to fetch holdings. Token may be expired.");
            }

            Map<String, Object> responseBody = response.getBody();
            String status = (String) responseBody.get("status");
            if (!"success".equals(status)) {
                conn.setStatus("TOKEN_EXPIRED");
                connectionRepository.save(conn);
                return Map.of("success", false, "message", "Kite API returned error status");
            }

            // Kite v3 returns data as a list directly (not nested under data.holdings)
            Object dataObj = responseBody.get("data");
            List<Map<String, Object>> kiteHoldings;
            if (dataObj instanceof List) {
                kiteHoldings = (List<Map<String, Object>>) dataObj;
            } else if (dataObj instanceof Map) {
                Map<String, Object> dataMap = (Map<String, Object>) dataObj;
                kiteHoldings = (List<Map<String, Object>>) dataMap.get("holdings");
            } else {
                return Map.of("success", true, "message", "No holdings found", "created", 0, "updated", 0);
            }

            if (kiteHoldings == null || kiteHoldings.isEmpty()) {
                return Map.of("success", true, "message", "No holdings found in Zerodha account",
                        "created", 0, "updated", 0);
            }

            // Create/find demat account for Zerodha
            DematAccount demat = getOrCreateDematAccount(userId, conn);
            conn.setDematAccountId(demat.getId());

            int created = 0;
            int updated = 0;
            int skipped = 0;
            int txnCreated = 0;

            for (Map<String, Object> kh : kiteHoldings) {
                try {
                    String tradingSymbol = (String) kh.get("tradingsymbol");
                    String isin = (String) kh.get("isin");
                    String exchange = (String) kh.get("exchange");
                    Number quantity = (Number) kh.get("quantity");
                    Number avgPrice = (Number) kh.get("average_price");
                    Number lastPrice = (Number) kh.get("last_price");

                    if (tradingSymbol == null || quantity == null || avgPrice == null) {
                        skipped++;
                        continue;
                    }

                    // Kite uses NSE/BSE exchange. Normalize symbol with .NS suffix
                    String symbol = tradingSymbol + ".NS";
                    BigDecimal qty = BigDecimal.valueOf(quantity.doubleValue());
                    BigDecimal avg = BigDecimal.valueOf(avgPrice.doubleValue());

                    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                        skipped++;
                        continue;
                    }

                    // Check existing by user + symbol + demat (prevents duplicates on re-sync)
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
                        holdingRepository.save(match);
                        updated++;

                        // Create synthetic BUY transaction if none from this broker exists
                        if (createSyntheticTransaction(match, userId, qty, avg)) txnCreated++;
                    } else {
                        // Create new holding
                        HoldingRequest req = HoldingRequest.builder()
                                .assetType(AssetType.EQUITY)
                                .symbol(symbol)
                                .name(tradingSymbol)
                                .quantity(qty)
                                .averageBuyPrice(avg)
                                .exchange(exchange)
                                .isin(isin)
                                .dematAccountId(demat.getId().toString())
                                .build();
                        holdingService.createHolding(userId.toString(), req);
                        created++;

                        // Create transaction for newly created holding
                        holdingRepository.findByUserIdAndSymbolAndDematAccountId(userId, symbol, demat.getId())
                                .ifPresent(h -> createSyntheticTransaction(h, userId, qty, avg));
                        txnCreated++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to sync Zerodha holding: {}", e.getMessage());
                    skipped++;
                }
            }

            conn.setLastSyncedAt(LocalDateTime.now());
            connectionRepository.save(conn);

            log.info("Zerodha sync for user {}: {} created, {} updated, {} skipped, {} transactions",
                    userId, created, updated, skipped, txnCreated);
            return Map.of("success", true,
                    "message", String.format("%d holdings (%d new, %d updated), %d transactions imported",
                            kiteHoldings.size(), created, updated, txnCreated),
                    "created", created, "updated", updated, "skipped", skipped,
                    "transactions", txnCreated, "total", kiteHoldings.size());
        } catch (Exception e) {
            log.error("Zerodha sync failed for user {}: {}", userId, e.getMessage());
            if (e.getMessage() != null && (e.getMessage().contains("403") || e.getMessage().contains("401"))) {
                conn.setStatus("TOKEN_EXPIRED");
                connectionRepository.save(conn);
            }
            return Map.of("success", false, "message", "Sync failed: " + e.getMessage());
        }
    }

    /**
     * Get connection status for the current user.
     */
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

    /**
     * Disconnect Zerodha — clear token and set status to DISCONNECTED.
     * Also invalidates the session on Kite side.
     */
    @Transactional
    public void disconnect(UUID userId) {
        connectionRepository.findByUserIdAndBroker(userId, BROKER_NAME)
                .ifPresent(conn -> {
                    // Try to invalidate session on Kite side (best effort)
                    if (conn.getAccessToken() != null) {
                        try {
                            HttpHeaders headers = buildAuthHeaders(conn.getAccessToken());
                            restTemplate.exchange(
                                    KITE_BASE_URL + "/session/token?api_key=" + apiKey + "&access_token=" + conn.getAccessToken(),
                                    HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
                        } catch (Exception e) {
                            log.debug("Failed to invalidate Zerodha session (best effort): {}", e.getMessage());
                        }
                    }
                    conn.setStatus("DISCONNECTED");
                    conn.setAccessToken(null);
                    connectionRepository.save(conn);
                });
    }

    // ---- Private helpers ----

    private HttpHeaders buildAuthHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Kite-Version", "3");
        headers.set("Authorization", "token " + apiKey + ":" + accessToken);
        headers.set("Accept", "application/json");
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchProfile(String accessToken) {
        HttpHeaders headers = buildAuthHeaders(accessToken);
        ResponseEntity<Map> response = restTemplate.exchange(PROFILE_URL, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return (Map<String, Object>) response.getBody().get("data");
        }
        return null;
    }

    private DematAccount getOrCreateDematAccount(UUID userId, BrokerConnection conn) {
        // Check if demat already linked
        if (conn.getDematAccountId() != null) {
            Optional<DematAccount> existing = dematAccountRepository.findById(conn.getDematAccountId());
            if (existing.isPresent()) return existing.get();
        }

        // Check if user already has a Zerodha demat
        List<DematAccount> demats = dematAccountRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Optional<DematAccount> zerodhaDemat = demats.stream()
                .filter(d -> "Zerodha".equalsIgnoreCase(d.getBrokerName()))
                .findFirst();
        if (zerodhaDemat.isPresent()) return zerodhaDemat.get();

        // Create new
        DematAccount demat = DematAccount.builder()
                .userId(userId)
                .brokerName("Zerodha")
                .accountNumber(conn.getBrokerUserId())
                .accountType("Equity")
                .description("Auto-created from Zerodha import")
                .isDefault(demats.isEmpty())
                .build();
        return dematAccountRepository.save(demat);
    }

    /**
     * Create a synthetic BUY transaction for a holding if no broker transaction exists yet.
     * Zerodha has no historical trades API, so we create a snapshot BUY at average cost.
     * Does NOT call CostBasisService since the holding already has correct qty/avgPrice from sync.
     */
    private boolean createSyntheticTransaction(Holding holding, UUID userId, BigDecimal qty, BigDecimal avgPrice) {
        // Skip if the holding already has ANY transaction, not merely one tagged with this
        // broker. Creating a holding now records an opening BUY of its own, which carries no
        // broker label; checking only for broker-tagged rows meant the sync added a second
        // BUY for the same units, leaving the ledger at twice the position the holding shows.
        // The same guard also stops a synthetic row duplicating a manually entered purchase.
        if (!transactionRepository.findByHoldingId(holding.getId()).isEmpty()) return false;

        Transaction txn = Transaction.builder()
                .holdingId(holding.getId())
                .userId(userId)
                .transactionType(TransactionType.BUY)
                .quantity(qty)
                .price(avgPrice)
                .amount(qty.multiply(avgPrice))
                .fees(BigDecimal.ZERO)
                .taxes(BigDecimal.ZERO)
                .transactionDate(LocalDateTime.now())
                .notes("Auto-imported from Zerodha holdings sync")
                .broker(BROKER_LABEL)
                .build();
        transactionRepository.save(txn);
        return true;
    }

    /**
     * SHA-256 hex digest for Kite Connect checksum.
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 computation failed", e);
        }
    }
}
