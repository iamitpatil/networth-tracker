package com.networth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networth.model.dto.HoldingResponse;
import com.networth.model.dto.TransactionRequest;
import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.service.market.PriceService;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AIChatService {

    private final HoldingService holdingService;
    private final TransactionService transactionService;
    private final HoldingRepository holdingRepository;
    private final PriceService priceService;
    private final ObjectMapper objectMapper;

    private static final String LLAMA_URL = "http://localhost:8082/v1/chat/completions";
    private static final String MODEL = "llama";
    private static final double TEMPERATURE = 0.3;

    public String chat(String userId, String message, String mode, List<ChatMessage> history) {
        String systemPrompt = buildSystemPrompt(mode, userId);

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", MODEL);
            body.put("temperature", TEMPERATURE);
            body.put("stream", false);

            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            if (history != null) {
                for (ChatMessage m : history) {
                    messages.addObject()
                            .put("role", m.user() ? "user" : "assistant")
                            .put("content", m.content());
                }
            }
            messages.addObject().put("role", "user").put("content", message);

            URI uri = URI.create(LLAMA_URL);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(120000);
            conn.setReadTimeout(120000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(objectMapper.writeValueAsBytes(body));
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                String errorStream = status >= 400 ? "ErrorStream" : "InputStream";
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        status >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
                    return "AI server error (" + status + "): " + br.lines().collect(Collectors.joining());
                }
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String responseBody = br.lines().collect(Collectors.joining());
                Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                    if (msg != null) {
                        return (String) msg.get("content");
                    }
                }
                return "Sorry, I couldn't process that. Please try again.";
            }
        } catch (java.net.ConnectException e) {
            return "AI model is not running. Start it with: `llama-server -m models/your-model.gguf --host 127.0.0.1 --port 8081`";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String buildSystemPrompt(String mode, String userId) {
        if ("import".equals(mode)) {
            return """
You are a financial transaction parser for the Net Worth Tracker app.

Your job: Convert the user's natural language transaction descriptions into CSV format matching this schema:

symbol,transactionType,quantity,price,transactionDate

Rules:
- transactionType: BUY, SELL, SIP, LUMPSUM, DEPOSIT, WITHDRAWAL, CONTRIBUTION
- transactionDate: YYYY-MM-DD format
- quantity: number of shares/units (for stocks/MFs) or 1 for lumpsum
- price: per-unit price or total amount
- Use .NS suffix for NSE stocks (e.g., RELIANCE.NS)
- Output ONLY the CSV lines, one per transaction, no explanation.
- If the input is unclear, ask clarifying questions instead.

Examples:
User: "bought 10 shares of reliance at 2800 on jan 15 2025"
CSV: RELIANCE.NS,BUY,10,2800,2025-01-15

User: "invested 5000 in axis bluechip fund on 1st feb"
CSV: INF090I01CS4,LUMPSUM,1,5000,2025-02-01
""";
        } else {
            String portfolio = getPortfolioSummary(userId);
            return """
You are a financial advisor for the Net Worth Tracker app. You have access to the user's portfolio data.

Current Portfolio:
%s

Provide helpful financial advice based on their holdings. Consider:
- Portfolio diversification across asset types
- Sector concentration (for stocks)
- Risk assessment
- Suggestions for rebalancing
- Tax-efficient investing tips
- SIP vs lumpsum recommendations

Be concise, practical, and focused on Indian market context.
""".formatted(portfolio);
        }
    }

    private String getPortfolioSummary(String userId) {
        try {
            List<HoldingResponse> holdings = holdingService.getUserHoldings(userId);
            if (holdings.isEmpty()) return "No holdings yet.";
            return holdings.stream()
                    .map(h -> String.format("- %s (%s): Rs.%.0f invested, current value Rs.%.0f",
                            h.getSymbol(), h.getAssetType(),
                            h.getQuantity().doubleValue() * h.getAverageBuyPrice().doubleValue(),
                            h.getCurrentValue() != null ? h.getCurrentValue().doubleValue() : 0))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Could not load portfolio.";
        }
    }

    public List<Map<String, Object>> executeImport(String userId, String message) {
        String csvText = chat(userId, message, "import", List.of());
        return processCsvLines(userId, csvText);
    }

    public List<Map<String, Object>> executeCsv(String userId, String csvText) {
        return processCsvLines(userId, csvText);
    }

    private List<Map<String, Object>> processCsvLines(String userId, String csvText) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (String line : csvText.split("\n")) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("CSV:") || line.startsWith("csv:")) continue;

            line = line.replaceAll("^\"|\"$", "");
            String[] parts = line.split(",");
            if (parts.length < 5) continue;

            try {
                String symbol = parts[0].replaceAll("^\"|\"$", "").strip();
                String typeStr = parts[1].replaceAll("^\"|\"$", "").strip();
                BigDecimal qty = new BigDecimal(parts[2].replaceAll("^\"|\"$", "").strip());
                BigDecimal price = new BigDecimal(parts[3].replaceAll("^\"|\"$", "").strip());
                LocalDateTime date = LocalDate.parse(
                        parts[4].replaceAll("^\"|\"$", "").strip(),
                        DateTimeFormatter.ISO_LOCAL_DATE
                ).atStartOfDay();

                List<Holding> holdings = holdingService.getHoldingsBySymbol(userId, symbol);
                boolean isNew = false;
                Holding holding;
                if (holdings.isEmpty()) {
                    holding = Holding.builder()
                            .userId(UUID.fromString(userId))
                            .assetType(inferAssetType(symbol))
                            .symbol(symbol)
                            .name(deriveName(symbol))
                            .quantity(BigDecimal.ZERO)
                            .averageBuyPrice(BigDecimal.ZERO)
                            .currentPrice(price)
                            .currentValue(BigDecimal.ZERO)
                            .realizedPnl(BigDecimal.ZERO)
                            .unrealizedPnl(BigDecimal.ZERO)
                            .currency("INR")
                            .build();
                    holding = holdingRepository.save(holding);
                    isNew = true;
                } else {
                    holding = holdings.get(0);
                }
                TransactionRequest req = TransactionRequest.builder()
                        .holdingId(holding.getId().toString())
                        .transactionType(TransactionType.valueOf(typeStr))
                        .quantity(qty)
                        .price(price)
                        .transactionDate(date)
                        .build();

                transactionService.addTransaction(userId, req);
                // Re-fetch holding from DB to get updated quantity after transaction
                Holding updated = holdingRepository.findById(holding.getId()).orElse(holding);
                if (updated.getIsin() == null && symbol.length() == 12) {
                    updated.setIsin(symbol);
                }
                BigDecimal livePrice = priceService.getCurrentPrice(symbol, updated.getAssetType());
                if (livePrice != null) {
                    updated.setCurrentPrice(livePrice);
                }
                updated.setCurrentValue(updated.getQuantity().multiply(updated.getCurrentPrice()));
                holdingRepository.save(updated);
                results.add(Map.of(
                        "status", "success",
                        "message", "Added " + symbol + " " + typeStr + " " + qty + " @ " + price,
                        "symbol", symbol,
                        "transactionType", typeStr,
                        "quantity", qty,
                        "price", price
                ));
            } catch (Exception e) {
                results.add(Map.of(
                        "status", "error",
                        "message", "Failed to process: " + line + " — " + e.getMessage()
                ));
            }
        }

        return results;
    }

    private AssetType inferAssetType(String symbol) {
        String upper = symbol.toUpperCase();
        if (upper.endsWith(".NS") || upper.endsWith(".BO")) return AssetType.EQUITY;
        if (upper.startsWith("INF") || upper.contains("MUTUAL") || upper.contains("MF")) return AssetType.MUTUAL_FUND;
        if (upper.contains("GOLD") || upper.contains("SGB")) return AssetType.SGB;
        if (upper.contains("FD") || upper.contains("FIXED")) return AssetType.FD;
        return AssetType.EQUITY;
    }

    private String deriveName(String symbol) {
        String name = symbol.replaceAll("\\.(NS|BO)$", "").replace("_", " ");
        if (name.length() <= 3) return name;
        StringBuilder readable = new StringBuilder();
        readable.append(Character.toUpperCase(name.charAt(0)));
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && !Character.isUpperCase(name.charAt(i - 1))) {
                readable.append(' ');
            }
            readable.append(c);
        }
        return readable.toString().trim();
    }

    public record ChatMessage(boolean user, String content) {}
}
