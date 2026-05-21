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
public class GenericExtractionNode implements ProcessingNode {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    private static final String PARSE_PROMPT = """
You are a financial data extractor. Extract all financial information from this document text.
Look for transactions, account details, card details, employer details, investment details.

Return a JSON object:
{
  "documentType": "best guess of document type",
  "entities": [
    { "type": "HOLDING|ACCOUNT|CARD|EMPLOYER|OTHER", "name": "...", "identifier": "..." }
  ],
  "transactions": [
    { "date": "YYYY-MM-DD", "description": "...", "amount": 0, "type": "CREDIT|DEBIT", "category": "..." }
  ],
  "financialSummary": {
    "totalCredits": 0, "totalDebits": 0,
    "startBalance": null, "endBalance": null
  },
  "notes": "Any other relevant information found"
}
""";

    @Override
    public String getName() {
        return "extractGeneric";
    }

    @Override
    public void process(GraphState state) {
        String text = state.getRawText();
        if (text == null || text.isBlank()) {
            state.setError("No text to extract data from");
            return;
        }

        String response = aiClient.chat(PARSE_PROMPT, text);
        if (response == null) {
            state.setExtractedData(Map.of("note", "AI server unavailable, extracted raw text only", "rawLength", text.length()));
            return;
        }

        try {
            String cleaned = response.replaceAll("```json\\s*|```\\s*", "").trim();
            Map<String, Object> result = objectMapper.readValue(cleaned, new TypeReference<>() {});
            state.setExtractedData(result);
            log.debug("Generic extraction complete: {} entities, {} transactions",
                    result.get("entities") instanceof java.util.List<?> e ? e.size() : 0,
                    result.get("transactions") instanceof java.util.List<?> t ? t.size() : 0);
        } catch (Exception e) {
            log.warn("Failed to parse generic extraction response: {}", e.getMessage());
            state.setExtractedData(Map.of("note", "Failed to parse AI extraction", "rawText", text.length() > 1000 ? text.substring(0, 1000) : text));
        }
    }
}
