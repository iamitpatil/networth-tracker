package com.networth.service.documentgraph.nodes;

import com.networth.service.documentgraph.AiClient;
import com.networth.service.documentgraph.GraphState;
import com.networth.service.documentgraph.ProcessingNode;
import com.networth.service.importservice.PDFStatementParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankStatementExtractionNode implements ProcessingNode {

    private final PDFStatementParser pdfStatementParser;

    @Override
    public String getName() {
        return "extractBankStmt";
    }

    @Override
    public void process(GraphState state) {
        byte[] fileContent = state.getFileContent();
        if (fileContent == null || fileContent.length == 0) {
            state.setError("No file content to extract bank statement from");
            return;
        }

        try {
            var multipartFile = new com.networth.service.documentgraph.ByteArrayMultipartFile(
                    "file", state.getOriginalFilename(), state.getContentType(), fileContent);
            List<String> transactions = pdfStatementParser.parseBankStatement(state.getUserId(), multipartFile);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("transactions", transactions);
            state.setExtractedData(result);
            log.debug("Extracted {} bank statement transactions", transactions.size());
        } catch (Exception e) {
            log.warn("Bank statement parsing failed: {}", e.getMessage());
            state.setError("Failed to parse bank statement: " + e.getMessage());
        }
    }
}
