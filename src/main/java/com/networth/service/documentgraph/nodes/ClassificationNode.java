package com.networth.service.documentgraph.nodes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.service.documentgraph.AiClient;
import com.networth.service.documentgraph.DocumentType;
import com.networth.service.documentgraph.GraphState;
import com.networth.service.documentgraph.ProcessingNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClassificationNode implements ProcessingNode {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
You are a financial document classifier. Given the text extracted from a document,
determine which category it belongs to.

Key heuristics by type:
- CREDIT_CARD_BILL: Contains "card" + "due date" + "minimum amount" + transaction table
- SALARY_SLIP: Contains "employee name" + "PAN" + "gross pay" + "net pay" + deductions
- BANK_STATEMENT: Contains bank name + account number + opening/closing balance + transactions
- CAS: Contains "Consolidated Account Statement" + multiple folios/holdings
- FORM_16: Contains "Form 16" + "TDS" + "PAN" + assessment year + salary breakdown
- BROKER_CSV: CSV with headers like Symbol, Quantity, Price
- INVOICE: Contains "invoice" + "bill to" + line items + total

Respond ONLY with a JSON object:
{
  "documentType": "CREDIT_CARD_BILL|SALARY_SLIP|BANK_STATEMENT|CAS|FORM_16|BROKER_CSV|INVOICE|GENERIC_FINANCIAL|UNKNOWN",
  "confidence": 0.0-1.0,
  "reasoning": "Brief explanation"
}
""";

    @Override
    public String getName() {
        return "classify";
    }

    @Override
    public void process(GraphState state) {
        String text = state.getRawText();
        if (text == null || text.isBlank()) {
            state.setClassifiedAs(DocumentType.UNKNOWN);
            state.setClassificationConfidence(0);
            state.setClassificationReasoning("No text to classify");
            return;
        }

        // Truncate very long text for classification
        String input = text.length() > 8000 ? text.substring(0, 8000) : text;

        String response = aiClient.chat(SYSTEM_PROMPT, input);
        if (response == null) {
            state.setClassifiedAs(DocumentType.UNKNOWN);
            state.setClassificationConfidence(0);
            state.setClassificationReasoning("AI server unavailable");
            return;
        }

        try {
            String cleaned = response.replaceAll("```json\\s*|```\\s*", "").trim();
            Map<String, Object> result = objectMapper.readValue(cleaned, new TypeReference<>() {});
            String typeStr = (String) result.getOrDefault("documentType", "UNKNOWN");
            Number conf = (Number) result.getOrDefault("confidence", 0);
            String reasoning = (String) result.getOrDefault("reasoning", "");

            state.setClassifiedAs(parseType(typeStr));
            state.setClassificationConfidence(conf.doubleValue());
            state.setClassificationReasoning(reasoning);

            log.debug("Classified as {} (confidence: {})", state.getClassifiedAs(), state.getClassificationConfidence());
        } catch (Exception e) {
            log.warn("Failed to parse classification response: {}", e.getMessage());
            state.setClassifiedAs(DocumentType.GENERIC_FINANCIAL);
            state.setClassificationConfidence(0.3);
            state.setClassificationReasoning("Failed to parse AI response, defaulting to generic");
        }
    }

    private DocumentType parseType(String typeStr) {
        if (typeStr == null) return DocumentType.UNKNOWN;
        try {
            return DocumentType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return DocumentType.UNKNOWN;
        }
    }
}
