package com.networth.controller;

import com.networth.service.documentgraph.DocumentProcessingOrchestrator;
import com.networth.service.documentgraph.ExecutedAction;
import com.networth.service.documentgraph.GraphState;
import com.networth.service.documentgraph.ProposedAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/documents/process")
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingController {

    private final DocumentProcessingOrchestrator orchestrator;
    private final Map<UUID, GraphState> sessions = new ConcurrentHashMap<>();

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> processDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "context", required = false) String context,
            @RequestParam(value = "autoExecute", defaultValue = "false") boolean autoExecute) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        UUID correlationId = UUID.randomUUID();

        try {
            GraphState state = GraphState.builder()
                    .userId(userId)
                    .correlationId(correlationId)
                    .originalFilename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .fileContent(file.getBytes())
                    .password(password)
                    .autoExecute(autoExecute)
                    .completed(false)
                    .build();

            // Run the graph synchronously for now
            // Future: make async with status polling
            orchestrator.execute(state);

            sessions.put(correlationId, state);

            if (state.getError() != null && state.getError().equals("PASSWORD_REQUIRED")) {
                return ResponseEntity.status(422).body(Map.of(
                        "correlationId", correlationId.toString(),
                        "status", "PASSWORD_REQUIRED",
                        "message", "This PDF is password protected. Please provide the password."
                ));
            }

            return ResponseEntity.ok(buildResponse(state));
        } catch (Exception e) {
            log.error("Document processing failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/{correlationId}/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID correlationId) {

        GraphState state = sessions.get(correlationId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(buildResponse(state));
    }

    @PostMapping("/{correlationId}/confirm")
    public ResponseEntity<Map<String, Object>> confirmActions(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID correlationId,
            @RequestBody Map<String, Object> body) {

        GraphState state = sessions.get(correlationId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        @SuppressWarnings("unchecked")
        List<Integer> actionIndexes = ((List<Integer>) body.getOrDefault("actions", List.of()));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> overrides = (Map<String, Map<String, Object>>) body.getOrDefault("overrides", Map.of());

        List<ProposedAction> filteredActions = new ArrayList<>();
        for (int idx : actionIndexes) {
            if (idx >= 0 && idx < state.getProposedActions().size()) {
                ProposedAction action = state.getProposedActions().get(idx);
                // Apply any overrides
                Map<String, Object> override = overrides.get("action-index-" + idx);
                if (override != null && override.containsKey("entityId")) {
                    action.setEntityId(UUID.fromString((String) override.get("entityId")));
                }
                filteredActions.add(action);
            }
        }

        state.setProposedActions(filteredActions);
        state.setAutoExecute(true);
        state.setRequiresHumanReview(false);
        orchestrator.execute(state);

        return ResponseEntity.ok(buildResponse(state));
    }

    private Map<String, Object> buildResponse(GraphState state) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("correlationId", state.getCorrelationId().toString());
        response.put("status", state.isCompleted() ? "COMPLETED" :
                state.getError() != null ? "ERROR" :
                        state.isRequiresHumanReview() ? "AWAITING_REVIEW" : "PROCESSING");
        response.put("currentNode", state.getCurrentNodeName());
        response.put("documentType", state.getClassifiedAs());
        response.put("classificationConfidence", state.getClassificationConfidence());
        response.put("classificationReasoning", state.getClassificationReasoning());

        if (state.getExtractedData() != null) {
            response.put("extractedData", summarizeExtractedData(state.getExtractedData()));
        }

        if (state.getMatchedEntities() != null) {
            response.put("matchedEntities", state.getMatchedEntities());
        }

        if (state.getProposedActions() != null) {
            response.put("proposedActions", state.getProposedActions());
        }

        if (state.getExecutedActions() != null) {
            response.put("executedActions", state.getExecutedActions());
        }

        response.put("requiresHumanReview", state.isRequiresHumanReview());

        // Generate a human-readable summary
        response.put("summary", generateSummary(state));

        if (state.getError() != null) {
            response.put("error", state.getError());
        }

        return response;
    }

    private Map<String, Object> summarizeExtractedData(Map<String, Object> data) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (data.containsKey("cardIssuer")) {
            summary.put("type", "Credit Card Bill");
            summary.put("issuer", data.get("cardIssuer"));
            summary.put("dueDate", data.get("dueDate"));
            summary.put("totalAmountDue", data.get("totalAmountDue"));
            summary.put("transactionCount",
                    data.get("transactions") instanceof List<?> l ? l.size() : 0);
        } else if (data.containsKey("employerName")) {
            summary.put("type", "Salary Slip");
            summary.put("employer", data.get("employerName"));
            summary.put("grossPay", data.get("grossPay"));
            summary.put("netPay", data.get("netPay"));
        } else if (data.containsKey("transactions")) {
            summary.put("type", "Bank Statement");
            summary.put("transactionCount",
                    data.get("transactions") instanceof List<?> l ? l.size() : 0);
        } else if (data.containsKey("grossSalary")) {
            summary.put("type", "Form 16");
            summary.put("employer", data.get("employerName"));
            summary.put("grossSalary", data.get("grossSalary"));
            summary.put("confidence", data.get("confidenceScore"));
        } else {
            summary.put("type", "Generic Financial Document");
            summary.put("dataKeys", data.keySet());
        }
        return summary;
    }

    private String generateSummary(GraphState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Detected ").append(state.getClassifiedAs());

        Map<String, Object> data = state.getExtractedData();
        if (data != null) {
            if (data.containsKey("cardIssuer")) {
                sb.append(" for ").append(data.get("cardIssuer"));
                if (data.get("transactions") instanceof List<?> t) {
                    sb.append(". ").append(t.size()).append(" transactions found");
                }
                if (data.get("totalAmountDue") instanceof Number n) {
                    sb.append(", total due: Rs.").append(n);
                }
            } else if (data.containsKey("employerName")) {
                sb.append(" for ").append(data.get("employerName"));
                if (data.get("netPay") instanceof Number n) {
                    sb.append(", net pay: Rs.").append(n);
                }
            } else if (data.containsKey("transactions")) {
                List<?> t = (List<?>) data.get("transactions");
                sb.append(". ").append(t.size()).append(" transactions found");
            }
        }

        if (state.getExecutedActions() != null && !state.getExecutedActions().isEmpty()) {
            long success = state.getExecutedActions().stream().filter(ExecutedAction::isSuccess).count();
            sb.append(". ").append(success).append("/").append(state.getExecutedActions().size()).append(" actions executed");
        }

        return sb.toString();
    }
}
