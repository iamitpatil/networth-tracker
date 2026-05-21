package com.networth.service.documentgraph.nodes;

import com.networth.service.documentgraph.GraphState;
import com.networth.service.documentgraph.ProcessingNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CasExtractionNode implements ProcessingNode {

    @Override
    public String getName() {
        return "extractCas";
    }

    @Override
    public void process(GraphState state) {
        // CAS parsing is complex; for Phase 1 we extract text and let the generic node handle it
        // Full CAS parser integration can reuse PDFStatementParser.parseCAS() in Phase 2
        String text = state.getRawText();
        if (text == null || text.isBlank()) {
            state.setError("No text to extract CAS data from");
            return;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "CAS");
        result.put("rawLength", text.length());
        result.put("note", "Full CAS parsing requires dedicated regex extraction; data passed to generic extraction");
        state.setExtractedData(result);
        log.debug("CAS document extracted, {} chars", text.length());
    }
}
