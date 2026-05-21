package com.networth.service.documentgraph.mcp;

import com.networth.service.documentgraph.*;
import com.networth.service.documentgraph.nodes.PdfTextExtractionNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class McpDocumentProcessingOrchestrator {

    private final PdfTextExtractionNode pdfTextExtractionNode;
    private final McpToolClient toolClient;
    private final com.networth.service.documentgraph.nodes.RoutingNode routingNode;

    public GraphState execute(GraphState state) {
        log.debug("Starting MCP-based document processing for correlationId={}", state.getCorrelationId());

        // 1. Extract text from PDF/file (direct call, no MCP needed)
        pdfTextExtractionNode.process(state);
        if (state.getError() != null) {
            log.warn("Graph failed at pdfTextExtract: {}", state.getError());
            state.setCompleted(true);
            return state;
        }

        // 2. Classify via MCP tool
        state.setCurrentNodeName("classify");
        Map<String, Object> classifyResult = toolClient.callTool("classify_document",
                Map.of("text", state.getRawText()));
        String typeStr = (String) classifyResult.getOrDefault("documentType", "UNKNOWN");
        state.setClassifiedAs(parseType(typeStr));
        state.setClassificationConfidence(((Number) classifyResult.getOrDefault("confidence", 0)).doubleValue());
        state.setClassificationReasoning((String) classifyResult.getOrDefault("reasoning", ""));
        log.debug("MCP classified as {} (confidence: {})", state.getClassifiedAs(), state.getClassificationConfidence());

        if (state.getClassifiedAs() == DocumentType.UNKNOWN) {
            state.setCompleted(true);
            return state;
        }

        // 3. Extract via MCP tool (select tool by type)
        state.setCurrentNodeName("extract");
        String extractTool = extractionToolFor(state.getClassifiedAs());
        Map<String, Object> extractedData = toolClient.callTool(extractTool, Map.of("text", state.getRawText()));
        if (extractedData.containsKey("error")) {
            log.warn("MCP extraction failed: {}", extractedData.get("error"));
        }
        state.setExtractedData(extractedData);
        log.debug("MCP extracted data via {}", extractTool);

        // 4. Resolve entities via MCP tool
        state.setCurrentNodeName("entityResolve");
        Map<String, Object> resolveArgs = new java.util.LinkedHashMap<>();
        resolveArgs.put("userId", state.getUserId().toString());
        resolveArgs.put("extractedData", state.getExtractedData());
        Object resolveResult = toolClient.callTool("resolve_entity", resolveArgs);
        if (resolveResult instanceof List<?> matches) {
            List<MatchedEntity> entities = matches.stream()
                    .filter(m -> m instanceof Map<?, ?>)
                    .map(m -> {
                        Map<?, ?> mm = (Map<?, ?>) m;
                        String eid = (String) mm.get("entityId");
                        return MatchedEntity.builder()
                                .entityType((String) mm.get("entityType"))
                                .entityId(eid != null ? java.util.UUID.fromString(eid) : null)
                                .entityLabel((String) mm.get("entityLabel"))
                                .matchConfidence(mm.get("matchConfidence") instanceof Number n ? n.doubleValue() : 0.0)
                                .build();
                    })
                    .toList();
            state.setMatchedEntities(entities);
            log.debug("MCP resolved {} entities", entities.size());
        }

        // 5. Route (direct logic, same as before)
        routingNode.process(state);

        // 6. Execute via MCP tools for each proposed action
        if (state.isAutoExecute() && !state.isRequiresHumanReview()
                && state.getProposedActions() != null) {
            state.setCurrentNodeName("execute");
            var executedActions = state.getProposedActions().stream()
                    .map(action -> executeActionViaMcp(state.getUserId().toString(), action))
                    .toList();
            state.setExecutedActions(executedActions);
            log.debug("MCP executed {}/{} actions",
                    executedActions.stream().filter(ExecutedAction::isSuccess).count(), executedActions.size());
        }

        state.setCompleted(true);
        return state;
    }

    private String extractionToolFor(DocumentType type) {
        return switch (type) {
            case CREDIT_CARD_BILL -> "extract_credit_card_bill";
            case SALARY_SLIP -> "extract_salary_slip";
            case BANK_STATEMENT -> "extract_bank_statement";
            case CAS -> "extract_cas";
            case FORM_16 -> "extract_form16";
            default -> "extract_generic";
        };
    }

    private ExecutedAction executeActionViaMcp(String userId, ProposedAction action) {
        String mcpTool = mcpToolFor(action.getType());
        if (mcpTool == null || !toolClient.hasTool(mcpTool)) {
            return ExecutedAction.builder()
                    .action(action).success(true)
                    .result(Map.of("note", "Action type " + action.getType() + " not yet implemented via MCP"))
                    .build();
        }

        try {
            Map<String, Object> args = new java.util.LinkedHashMap<>(action.getData());
            args.putIfAbsent("userId", userId);
            if (action.getEntityId() != null) args.putIfAbsent("holdingId", action.getEntityId().toString());

            Map<String, Object> result = toolClient.callTool(mcpTool, args);
            boolean success = !result.containsKey("error");
            return ExecutedAction.builder()
                    .action(action).success(success)
                    .result(success ? result : null)
                    .errorMessage(success ? null : (String) result.get("error"))
                    .build();
        } catch (Exception e) {
            return ExecutedAction.builder()
                    .action(action).success(false)
                    .errorMessage(e.getMessage()).build();
        }
    }

    private String mcpToolFor(ActionType type) {
        return switch (type) {
            case CREATE_TRANSACTION -> "create_transaction";
            case UPDATE_CC_SPEND -> "update_cc_spend";
            case UPDATE_SALARY -> "update_salary";
            case UPDATE_HOLDING -> "update_holding";
            case UPDATE_ACCOUNT_BALANCE -> "update_account_balance";
            case LINK_DOCUMENT -> "link_document";
            default -> null;
        };
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
