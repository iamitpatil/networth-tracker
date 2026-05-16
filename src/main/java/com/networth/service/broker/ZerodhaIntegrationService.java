package com.networth.service.broker;

import com.networth.model.dto.HoldingRequest;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.model.enums.TransactionType;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZerodhaIntegrationService {

    private final RestTemplate restTemplate;
    private final HoldingService holdingService;
    private final TransactionService transactionService;

    @Value("${broker.zerodha.api-key:}")
    private String apiKey;

    @Value("${broker.zerodha.api-secret:}")
    private String apiSecret;

    @Value("${broker.zerodha.access-token:}")
    private String accessToken;

    private static final String ZERODHA_BASE_URL = "https://api.kite.trade";

    public List<Map<String, Object>> fetchHoldings(UUID userId) {
        List<Map<String, Object>> syncedHoldings = new ArrayList<>();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Kite-Version", "3");
            headers.set("Authorization", "token " + accessToken);

            ResponseEntity<Map> response = restTemplate.exchange(
                    ZERODHA_BASE_URL + "/portfolio/holdings",
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                Map data = (Map) body.get("data");

                if (data != null && data.containsKey("holdings")) {
                    List<Map> holdings = (List<Map>) data.get("holdings");

                    for (Map holding : holdings) {
                        String tradingsymbol = holding.get("tradingsymbol").toString();
                        double quantity = ((Number) holding.get("quantity")).doubleValue();
                        double averagePrice = ((Number) holding.get("average_price")).doubleValue();
                        double currentPrice = ((Number) holding.get("last_price")).doubleValue();
                        double pnl = ((Number) holding.get("pnl")).doubleValue();

                        HoldingRequest request = HoldingRequest.builder()
                                .assetType(AssetType.EQUITY)
                                .symbol(tradingsymbol)
                                .name(holding.get("company_name").toString())
                                .quantity(BigDecimal.valueOf(quantity))
                                .averageBuyPrice(BigDecimal.valueOf(averagePrice))
                                .exchange(holding.get("exchange").toString())
                                .build();

                        syncedHoldings.add(Map.of(
                                "symbol", tradingsymbol,
                                "quantity", quantity,
                                "averagePrice", averagePrice,
                                "currentPrice", currentPrice,
                                "pnl", pnl,
                                "status", "synced"
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch Zerodha holdings: {}", e.getMessage());
        }

        return syncedHoldings;
    }

    public List<Map<String, Object>> fetchPositions(UUID userId) {
        List<Map<String, Object>> positions = new ArrayList<>();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Kite-Version", "3");
            headers.set("Authorization", "token " + accessToken);

            ResponseEntity<Map> response = restTemplate.exchange(
                    ZERODHA_BASE_URL + "/portfolio/positions",
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                Map data = (Map) body.get("data");

                if (data != null && data.containsKey("net")) {
                    List<Map> netPositions = (List<Map>) data.get("net");

                    for (Map position : netPositions) {
                        positions.add(Map.of(
                                "symbol", position.get("tradingsymbol"),
                                "quantity", position.get("quantity"),
                                "averagePrice", position.get("average_price"),
                                "lastPrice", position.get("last_price"),
                                "realizedPnl", position.get("realized"),
                                "unrealizedPnl", position.get("unrealized")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch Zerodha positions: {}", e.getMessage());
        }

        return positions;
    }

    public List<Map<String, Object>> fetchOrders(UUID userId, String fromDate, String toDate) {
        List<Map<String, Object>> orders = new ArrayList<>();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Kite-Version", "3");
            headers.set("Authorization", "token " + accessToken);

            ResponseEntity<Map> response = restTemplate.exchange(
                    ZERODHA_BASE_URL + "/orders",
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                List<Map> data = (List<Map>) body.get("data");

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                LocalDateTime from = LocalDateTime.parse(fromDate + "T00:00:00");
                LocalDateTime to = LocalDateTime.parse(toDate + "T23:59:59");

                if (data != null) {
                    for (Map order : data) {
                        String orderTime = order.get("order_timestamp").toString();
                        LocalDateTime txnTime = LocalDateTime.parse(orderTime.substring(0, 19));

                        if (!txnTime.isBefore(from) && !txnTime.isAfter(to)) {
                            orders.add(Map.of(
                                    "orderId", order.get("order_id"),
                                    "symbol", order.get("tradingsymbol"),
                                    "transactionType", order.get("transaction_type"),
                                    "quantity", order.get("quantity"),
                                    "price", order.get("average_price"),
                                    "status", order.get("status"),
                                    "orderType", order.get("variety"),
                                    "timestamp", txnTime
                            ));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch Zerodha orders: {}", e.getMessage());
        }

        return orders;
    }

    public Map<String, Object> syncAllHoldings(UUID userId) {
        List<Map<String, Object>> holdings = fetchHoldings(userId);

        int created = 0;
        int updated = 0;

        for (Map<String, Object> holding : holdings) {
            try {
                HoldingRequest request = HoldingRequest.builder()
                        .assetType(AssetType.EQUITY)
                        .symbol(holding.get("symbol").toString())
                        .quantity(new BigDecimal(holding.get("quantity").toString()))
                        .averageBuyPrice(new BigDecimal(holding.get("averagePrice").toString()))
                        .build();

                holdingService.createHolding(userId.toString(), request);
                created++;
            } catch (IllegalArgumentException e) {
                updated++;
            }
        }

        return Map.of(
                "total", holdings.size(),
                "created", created,
                "updated", updated,
                "status", "completed"
        );
    }

    public String generateLoginUrl(String requestToken) {
        return String.format("https://kite.zerodha.com/connect/login?api_key=%s&v=3", apiKey);
    }
}
