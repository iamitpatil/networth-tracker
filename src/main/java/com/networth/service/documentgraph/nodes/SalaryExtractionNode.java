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
public class SalaryExtractionNode implements ProcessingNode {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    private static final String PARSE_PROMPT = """
You are a salary slip parser. Extract all earnings, deductions, and net pay from the salary slip text below.
Return ONLY a valid JSON object with keys:
- employerName (string)
- payDate (string, YYYY-MM-DD format)
- grossPay (number)
- netPay (number)
- components (object mapping each line item name to its amount, e.g. {"Basic Pay": 50000, "HRA": 25000, "PF": 6000})

PayDate MUST be in YYYY-MM-DD format. Use null for missing values. No explanation, no markdown, just JSON.
""";

    @Override
    public String getName() {
        return "extractSalary";
    }

    @Override
    public void process(GraphState state) {
        String text = state.getRawText();
        if (text == null || text.isBlank()) {
            state.setError("No text to extract salary data from");
            return;
        }

        String response = aiClient.chat(PARSE_PROMPT, text);
        if (response == null) {
            state.setError("AI server unavailable for salary extraction");
            return;
        }

        try {
            String cleaned = response.replaceAll("```json\\s*|```\\s*", "").trim();
            Map<String, Object> result = objectMapper.readValue(cleaned, new TypeReference<>() {});
            state.setExtractedData(result);
            log.debug("Extracted salary slip: employer={}, gross={}", result.get("employerName"), result.get("grossPay"));
        } catch (Exception e) {
            log.warn("Failed to parse salary extraction response: {}", e.getMessage());
            state.setError("Failed to parse extracted salary data");
        }
    }
}
