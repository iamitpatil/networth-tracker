package com.networth.service.documentgraph;

import com.networth.service.documentgraph.nodes.ClassificationNode;
import com.networth.service.documentgraph.nodes.EntityResolutionNode;
import com.networth.service.documentgraph.nodes.ExecutionNode;
import com.networth.service.documentgraph.nodes.ExtractionNodeFactory;
import com.networth.service.documentgraph.nodes.PdfTextExtractionNode;
import com.networth.service.documentgraph.nodes.RoutingNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingOrchestrator {

    private final PdfTextExtractionNode pdfTextExtractNode;
    private final ClassificationNode classificationNode;
    private final ExtractionNodeFactory extractionNodeFactory;
    private final EntityResolutionNode entityResolutionNode;
    private final RoutingNode routingNode;
    private final ExecutionNode executionNode;

    private Map<String, ProcessingNode> nodeMap;

    @PostConstruct
    public void init() {
        nodeMap = new HashMap<>();
        nodeMap.put("pdfTextExtract", pdfTextExtractNode);
        nodeMap.put("classify", classificationNode);
        nodeMap.put("entityResolve", entityResolutionNode);
        nodeMap.put("route", routingNode);
        nodeMap.put("execute", executionNode);
    }

    public GraphState execute(GraphState state) {
        log.debug("Starting document processing graph for correlationId={}", state.getCorrelationId());

        // 1. Extract text from PDF/file
        run("pdfTextExtract", state);
        if (state.getError() != null) {
            log.warn("Graph failed at pdfTextExtract: {}", state.getError());
            state.setCompleted(true);
            return state;
        }

        // 2. Classify document type
        run("classify", state);
        if (state.getClassifiedAs() == DocumentType.UNKNOWN) {
            log.debug("Document type unknown, skipping extraction");
            state.setCompleted(true);
            return state;
        }

        // 3. Extract structured data using the appropriate extraction node
        state.setCurrentNodeName("extract");
        extractionNodeFactory.nodeFor(state.getClassifiedAs()).process(state);
        if (state.getError() != null) {
            log.warn("Graph failed at extract: {}", state.getError());
            state.setCompleted(true);
            return state;
        }

        // 4. Resolve entities against user's existing data
        run("entityResolve", state);

        // 5. Route: decide what actions to propose
        run("route", state);

        // 6. Execute if autoExecute=true and no human review needed
        if (state.isAutoExecute() && !state.isRequiresHumanReview()) {
            run("execute", state);
        }

        state.setCompleted(true);
        log.debug("Graph completed: {} proposed actions, {} executed, requiresReview={}",
                state.getProposedActions() != null ? state.getProposedActions().size() : 0,
                state.getExecutedActions() != null ? state.getExecutedActions().size() : 0,
                state.isRequiresHumanReview());
        return state;
    }

    private void run(String nodeName, GraphState state) {
        state.setCurrentNodeName(nodeName);
        ProcessingNode node = nodeMap.get(nodeName);
        if (node != null) {
            node.process(state);
        }
    }
}
