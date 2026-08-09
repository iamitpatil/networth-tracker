package com.networth.service.documentgraph.nodes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.service.documentgraph.AiClient;
import com.networth.service.documentgraph.GraphState;
import com.networth.service.documentgraph.ProcessingNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditCardExtractionNode implements ProcessingNode {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    private static final String PARSE_PROMPT = """
You are a credit card bill/statement parser. Extract structured data from the text below.
Return ONLY a valid JSON object with these keys:
- cardIssuer (string, e.g. "HDFC Bank", "ICICI Bank", "SBI Card")
- cardLastFourDigits (string, last 4 digits of card number if found, else null)
- cardType (string, e.g. "VISA", "Mastercard", "RuPay", or null)
- statementDate (string, YYYY-MM-DD format, the billing/statement date)
- dueDate (string, YYYY-MM-DD format, payment due date)
- totalAmountDue (number, total amount due)
- minimumAmountDue (number, minimum payment required, or null)
- previousBalance (number, opening/previous balance, or null)
- paymentsReceived (number, payments/credits received, or null)
- newCharges (number, total new charges, or null)
- transactions (array of objects, each with:
    - date (string, YYYY-MM-DD)
    - description (string, merchant/transaction description)
    - amount (number, positive for charges, negative for credits/refunds)
    - category (string, one of: FOOD, SHOPPING, TRAVEL, FUEL, ENTERTAINMENT, UTILITIES, GROCERIES, HEALTH, EDUCATION, EMI, SUBSCRIPTION, INSURANCE, TRANSFER, OTHER)
)
- spendSummary (object mapping category to total amount, e.g. {"FOOD": 5200, "SHOPPING": 12000})

Categorize each transaction intelligently based on the merchant name/description.
Use null for any field you cannot determine. No explanation, no markdown fences, just the JSON object.
""";

    @Override
    public String getName() {
        return "extractCCBill";
    }

    @Override
    public void process(GraphState state) {
        String text = state.getRawText();
        if (text == null || text.isBlank()) {
            state.setError("No text to extract credit card data from");
            return;
        }

        String response = aiClient.chat(PARSE_PROMPT, text);
        if (response == null) {
            state.setError("AI server unavailable for credit card extraction");
            return;
        }

        try {
            String cleaned = response.replaceAll("```json\\s*|```\\s*", "").trim();
            Map<String, Object> result = objectMapper.readValue(cleaned, new TypeReference<>() {});
            state.setExtractedData(result);
            log.debug("Extracted CC bill: issuer={}, transactions={}",
                    result.get("cardIssuer"),
                    result.get("transactions") instanceof java.util.List<?> l ? l.size() : 0);
        } catch (Exception e) {
            log.warn("Failed to parse CC extraction response: {}", e.getMessage());
            state.setError("Failed to parse extracted credit card data");
        }
    }
}
