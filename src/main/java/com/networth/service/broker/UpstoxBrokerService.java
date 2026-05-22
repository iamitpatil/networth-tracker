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
    private final TransactionRepository transactionRepository;
    private final HoldingService holdingService;

    private static final String BROKER_NAME = "UPSTOX";
    private static final String BROKER_LABEL = "Upstox";
    private static final String AUTH_URL = "https://api.upstox.com/v2/login/authorization/dialog";
    private static final String TOKEN_URL = "https://api.upstox.com/v2/login/authorization/token";
    private static final String HOLDINGS_URL = "https://api.upstox.com/v2/portfolio/long-term-holdings";
    private static final String TRADES_URL = "https://api.upstox.com/v2/charges/historical-trades";
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
            int txnCreated = 0;

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

                        // Create synthetic BUY transaction if none from this broker exists
                        if (createSyntheticTransaction(match, userId, qty, avg)) txnCreated++;
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

                        // Create transaction for newly created holding
                        holdingRepository.findByUserIdAndSymbolAndDematAccountId(userId, symbol, demat.getId())
                                .ifPresent(h -> { if (createSyntheticTransaction(h, userId, qty, avg)) {}; });
                        txnCreated++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to sync holding: {}", e.getMessage());
                    skipped++;
                }
            }

            // Try to sync actual historical trades (best effort, won't fail the whole sync)
            int historicalTxns = 0;
            try {
                historicalTxns = syncHistoricalTrades(userId, conn, demat);
            } catch (Exception e) {
                log.warn("Historical trades sync failed (non-fatal): {}", e.getMessage());
            }

            conn.setLastSyncedAt(LocalDateTime.now());
            connectionRepository.save(conn);

            int totalTxns = txnCreated + historicalTxns;
            log.info("Upstox sync for user {}: {} created, {} updated, {} skipped, {} transactions",
                    userId, created, updated, skipped, totalTxns);
            return Map.of("success", true,
                    "message", String.format("%d holdings (%d new, %d updated), %d transactions imported",
                            upstoxHoldings.size(), created, updated, totalTxns),
                    "created", created, "updated", updated, "skipped", skipped,
                    "transactions", totalTxns, "total", upstoxHoldings.size());
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

    /**
     * Create a synthetic BUY transaction for a holding if no broker transaction exists yet.
     * This is a "snapshot" transaction representing the current position at average cost.
     * Does NOT call CostBasisService since the holding already has correct qty/avgPrice from sync.
     */
    private boolean createSyntheticTransaction(Holding holding, UUID userId, BigDecimal qty, BigDecimal avgPrice) {
        // Skip if broker transactions already exist for this holding
        List<Transaction> existing = transactionRepository.findByHoldingIdAndBroker(holding.getId(), BROKER_LABEL);
        if (!existing.isEmpty()) return false;

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
                .notes("Auto-imported from Upstox holdings sync")
                .broker(BROKER_LABEL)
                .build();
        transactionRepository.save(txn);
        return true;
    }

    /**
     * Fetch historical trades from Upstox's /v2/charges/historical-trades API.
     * Covers last 3 financial years. Creates Transaction records for each trade.
     * Returns count of new transactions created.
     */
    @SuppressWarnings("unchecked")
    private int syncHistoricalTrades(UUID userId, BrokerConnection conn, DematAccount demat) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + conn.getAccessToken());
        headers.set("Accept", "application/json");

        // Fetch equity segment trades for last 3 years
        java.time.LocalDate endDate = java.time.LocalDate.now();
        java.time.LocalDate startDate = endDate.minusYears(3);

        int totalCreated = 0;
        int page = 1;
        int pageSize = 500;

        while (true) {
            try {
                String url = TRADES_URL + "?segment=EQ"
                        + "&start_date=" + startDate
                        + "&end_date=" + endDate
                        + "&page_number=" + page
                        + "&page_size=" + pageSize;

                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
                        new HttpEntity<>(headers), Map.class);

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) break;

                Map<String, Object> body = response.getBody();
                String status = body.get("status") != null ? body.get("status").toString() : "";
                if (!"success".equals(status)) break;

                Map<String, Object> data = (Map<String, Object>) body.get("data");
                if (data == null) break;

                List<Map<String, Object>> trades = (List<Map<String, Object>>) data.get("trades");
                if (trades == null || trades.isEmpty()) break;

                for (Map<String, Object> trade : trades) {
                    try {
                        String tradingSymbol = (String) trade.get("scrip_name");
                        if (tradingSymbol == null) tradingSymbol = (String) trade.get("symbol");
                        String txnType = (String) trade.get("transaction_type"); // BUY or SELL
                        Number tradeQty = (Number) trade.get("quantity");
                        Number tradePrice = (Number) trade.get("price");
                        String tradeDateStr = trade.get("trade_date") != null ? trade.get("trade_date").toString() : null;

                        if (tradingSymbol == null || tradeQty == null || tradePrice == null) continue;

                        String symbol = tradingSymbol.contains(".") ? tradingSymbol : tradingSymbol + ".NS";
                        BigDecimal qty = BigDecimal.valueOf(tradeQty.doubleValue());
                        BigDecimal price = BigDecimal.valueOf(tradePrice.doubleValue());

                        // Parse trade date
                        LocalDateTime tradeDate;
                        if (tradeDateStr != null) {
                            try {
                                tradeDate = java.time.LocalDate.parse(tradeDateStr).atStartOfDay();
                            } catch (Exception e) {
                                tradeDate = LocalDateTime.now();
                            }
                        } else {
                            tradeDate = LocalDateTime.now();
                        }

                        TransactionType type = "SELL".equalsIgnoreCase(txnType) ? TransactionType.SELL : TransactionType.BUY;

                        // Find the holding this trade belongs to
                        Optional<Holding> holdingOpt = holdingRepository
                                .findByUserIdAndSymbolAndDematAccountId(userId, symbol, demat.getId());
                        if (holdingOpt.isEmpty()) continue; // No matching holding, skip

                        Holding holding = holdingOpt.get();
                        BigDecimal txnQty = type == TransactionType.SELL ? qty.negate() : qty;

                        // Dedup: check if this exact trade already exists
                        if (transactionRepository.existsByHoldingIdAndBrokerAndTransactionDateAndQuantity(
                                holding.getId(), BROKER_LABEL, tradeDate, txnQty)) {
                            continue;
                        }

                        Transaction txn = Transaction.builder()
                                .holdingId(holding.getId())
                                .userId(userId)
                                .transactionType(type)
                                .quantity(txnQty)
                                .price(price)
                                .amount(qty.multiply(price))
                                .fees(BigDecimal.ZERO)
                                .taxes(BigDecimal.ZERO)
                                .transactionDate(tradeDate)
                                .notes("Imported from Upstox trade history")
                                .broker(BROKER_LABEL)
                                .build();
                        transactionRepository.save(txn);
                        totalCreated++;
                    } catch (Exception e) {
                        log.debug("Skipped trade: {}", e.getMessage());
                    }
                }

                // Check if there are more pages
                if (trades.size() < pageSize) break;
                page++;
            } catch (Exception e) {
                log.warn("Historical trades page {} failed: {}", page, e.getMessage());
                break;
            }
        }

        if (totalCreated > 0) {
            log.info("Upstox historical trades for user {}: {} transactions imported", userId, totalCreated);
            // Remove synthetic transactions if real trades were found
            // (synthetic ones have notes "Auto-imported from Upstox holdings sync")
            cleanupSyntheticTransactions(userId, demat);
        }

        return totalCreated;
    }

    /**
     * If we got real historical trades, remove the synthetic BUY transactions we created as placeholders.
     */
    private void cleanupSyntheticTransactions(UUID userId, DematAccount demat) {
        List<Holding> holdings = holdingRepository.findByUserIdAndDematAccountId(userId, demat.getId());
        for (Holding h : holdings) {
            List<Transaction> brokerTxns = transactionRepository.findByHoldingIdAndBroker(h.getId(), BROKER_LABEL);
            List<Transaction> synthetic = brokerTxns.stream()
                    .filter(t -> t.getNotes() != null && t.getNotes().contains("Auto-imported from Upstox holdings sync"))
                    .toList();
            List<Transaction> real = brokerTxns.stream()
                    .filter(t -> t.getNotes() == null || !t.getNotes().contains("Auto-imported from Upstox holdings sync"))
                    .toList();
            // Only remove synthetic if real trades exist for this holding
            if (!real.isEmpty() && !synthetic.isEmpty()) {
                transactionRepository.deleteAll(synthetic);
                log.debug("Removed {} synthetic transactions for holding {} (replaced by real trades)", synthetic.size(), h.getSymbol());
            }
        }
    }
}
