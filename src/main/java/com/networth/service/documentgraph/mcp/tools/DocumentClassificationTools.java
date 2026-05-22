package com.networth.service.documentgraph.mcp.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.service.documentgraph.AiClient;
import com.networth.service.documentgraph.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentClassificationTools {

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
- NPS_STATEMENT: Contains "PRAN" or "National Pension" or "NPS" + "Tier" + "NAV" + contribution/transaction details, fund manager names (SBI/LIC/HDFC/UTI/Kotak/ICICI/Birla/Tata/Axis/DSP pension fund)
- BROKER_CSV: CSV with headers like Symbol, Quantity, Price
- INVOICE: Contains "invoice" + "bill to" + line items + total

Respond ONLY with a JSON object:
{
  "documentType": "CREDIT_CARD_BILL|SALARY_SLIP|BANK_STATEMENT|CAS|FORM_16|NPS_STATEMENT|BROKER_CSV|INVOICE|GENERIC_FINANCIAL|UNKNOWN",
  "confidence": 0.0-1.0,
  "reasoning": "Brief explanation"
}
""";

    @Tool(name = "classify_document", description = "Classify a financial document by its text content into a predefined document type")
    public Map<String, Object> classifyDocument(
            @ToolParam(description = "Extracted text from the document") String text) {
        if (text == null || text.isBlank()) {
            return Map.of("documentType", "UNKNOWN", "confidence", 0.0, "reasoning", "No text to classify");
        }

        String input = text.length() > 8000 ? text.substring(0, 8000) : text;
        String response = aiClient.chat(SYSTEM_PROMPT, input);

        if (response == null) {
            return Map.of("documentType", "UNKNOWN", "confidence", 0.0, "reasoning", "AI server unavailable");
        }

        try {
            String cleaned = response.replaceAll("```json\\s*|```\\s*", "").trim();
            return objectMapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse classification response: {}", e.getMessage());
            return Map.of("documentType", "GENERIC_FINANCIAL", "confidence", 0.3,
                    "reasoning", "Failed to parse AI response, defaulting to generic");
        }
    }
}
