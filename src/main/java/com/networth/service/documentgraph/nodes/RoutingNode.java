package com.networth.service.documentgraph.nodes;

import com.networth.service.documentgraph.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class RoutingNode implements ProcessingNode {

    @Override
    public String getName() {
        return "route";
    }

    @Override
    public void process(GraphState state) {
        List<ProposedAction> actions = new ArrayList<>();
        Map<String, Object> data = state.getExtractedData();
        List<MatchedEntity> entities = state.getMatchedEntities();
        DocumentType docType = state.getClassifiedAs();

        if (data == null || data.isEmpty()) {
            state.setProposedActions(List.of());
            return;
        }

        switch (docType) {
            case CREDIT_CARD_BILL -> routeCreditCardBill(actions, data, entities);
            case SALARY_SLIP -> routeSalarySlip(actions, data, entities);
            case BANK_STATEMENT -> routeBankStatement(actions, data, entities);
            case CAS -> routeCas(actions, data, entities);
            case FORM_16 -> routeForm16(actions, data, entities);
            default -> routeGeneric(actions, data, entities);
        }

        state.setProposedActions(actions);

        boolean hasLow = actions.stream().anyMatch(a -> a.getConfidence() == Confidence.LOW);
        state.setRequiresHumanReview(hasLow);

        log.debug("Routed {} proposed actions (requiresReview: {})", actions.size(), state.isRequiresHumanReview());
    }

    private void routeCreditCardBill(List<ProposedAction> actions, Map<String, Object> data, List<MatchedEntity> entities) {
        UUID cardId = findEntityId(entities, "CREDIT_CARD");
        double spendAmount = doubleField(data, "totalAmountDue");

        if (data.containsKey("transactions") || data.containsKey("totalAmountDue")) {
            ProposedAction.ProposedActionBuilder action = ProposedAction.builder()
                    .type(ActionType.UPDATE_CC_SPEND)
                    .entityType("CREDIT_CARD")
                    .entityId(cardId)
                    .data(data);

            if (cardId != null && spendAmount > 0) {
                action.confidence(Confidence.HIGH);
            } else if (cardId != null || spendAmount > 0) {
                action.confidence(Confidence.MEDIUM);
            } else {
                action.confidence(Confidence.LOW);
            }

            actions.add(action.build());
        }
    }

    private void routeSalarySlip(List<ProposedAction> actions, Map<String, Object> data, List<MatchedEntity> entities) {
        UUID salaryId = findEntityId(entities, "SALARY");
        Number grossPay = (Number) data.getOrDefault("grossPay", null);
        Number netPay = (Number) data.getOrDefault("netPay", null);

        if (grossPay != null || netPay != null) {
            ProposedAction.ProposedActionBuilder action = ProposedAction.builder()
                    .type(ActionType.UPDATE_SALARY)
                    .entityType("SALARY")
                    .entityId(salaryId)
                    .data(data);

            if (salaryId != null && grossPay != null) {
                action.confidence(Confidence.HIGH);
            } else if (salaryId != null || grossPay != null) {
                action.confidence(Confidence.MEDIUM);
            } else {
                action.confidence(Confidence.LOW);
            }

            actions.add(action.build());
        }
    }

    private void routeBankStatement(List<ProposedAction> actions, Map<String, Object> data, List<MatchedEntity> entities) {
        UUID accountId = findEntityId(entities, "BANK_ACCOUNT");
        List<Map<String, Object>> transactions = (List<Map<String, Object>>) data.getOrDefault("transactions", List.of());

        if (!transactions.isEmpty()) {
            ProposedAction.ProposedActionBuilder action = ProposedAction.builder()
                    .type(ActionType.CREATE_TRANSACTION)
                    .entityType("BANK_ACCOUNT")
                    .entityId(accountId)
                    .data(Map.of("transactions", transactions, "count", transactions.size()));

            action.confidence(accountId != null ? Confidence.HIGH : Confidence.MEDIUM);
            actions.add(action.build());
        }
    }

    private void routeCas(List<ProposedAction> actions, Map<String, Object> data, List<MatchedEntity> entities) {
        // CAS can contain multiple holdings + transactions
        // For Phase 1, propose as generic financial document
        List<Map<String, Object>> transactions = (List<Map<String, Object>>) data.getOrDefault("transactions", List.of());

        if (!transactions.isEmpty()) {
            actions.add(ProposedAction.builder()
                    .type(ActionType.CREATE_TRANSACTION)
                    .entityType("PORTFOLIO")
                    .data(Map.of("transactions", transactions, "count", transactions.size()))
                    .confidence(Confidence.MEDIUM)
                    .build());
        }

        actions.add(ProposedAction.builder()
                .type(ActionType.UPDATE_HOLDING)
                .entityType("PORTFOLIO")
                .data(Map.of("note", "CAS document processed", "holdings", data.getOrDefault("holdings", List.of())))
                .confidence(Confidence.MEDIUM)
                .build());
    }

    private void routeForm16(List<ProposedAction> actions, Map<String, Object> data, List<MatchedEntity> entities) {
        actions.add(ProposedAction.builder()
                .type(ActionType.UPDATE_USER_TAX_INFO)
                .entityType("USER")
                .data(data)
                .confidence(Confidence.HIGH)
                .build());
    }

    private void routeGeneric(List<ProposedAction> actions, Map<String, Object> data, List<MatchedEntity> entities) {
        List<Map<String, Object>> transactions = (List<Map<String, Object>>) data.getOrDefault("transactions", List.of());

        if (!transactions.isEmpty()) {
            UUID matchedAccountId = findEntityId(entities, "HOLDING");
            if (matchedAccountId == null) matchedAccountId = findEntityId(entities, "BANK_ACCOUNT");

            ProposedAction.ProposedActionBuilder action = ProposedAction.builder()
                    .type(ActionType.CREATE_TRANSACTION)
                    .data(Map.of("transactions", transactions, "count", transactions.size()));

            if (matchedAccountId != null) {
                action.entityId(matchedAccountId);
                action.entityType("HOLDING");
                action.confidence(Confidence.MEDIUM);
            } else {
                action.confidence(Confidence.LOW);
            }

            actions.add(action.build());
        }

        if (entities.isEmpty() && transactions.isEmpty() && !data.isEmpty()) {
            actions.add(ProposedAction.builder()
                    .type(ActionType.NOTHING)
                    .data(Map.of("note", "Document analyzed but no actionable data found"))
                    .confidence(Confidence.LOW)
                    .build());
        }
    }

    private UUID findEntityId(List<MatchedEntity> entities, String type) {
        return entities.stream()
                .filter(e -> type.equals(e.getEntityType()) && e.getEntityId() != null)
                .findFirst()
                .map(MatchedEntity::getEntityId)
                .orElse(null);
    }

    private double doubleField(Map<String, Object> map, String key) {
        Number val = (Number) map.get(key);
        return val != null ? val.doubleValue() : 0;
    }
}
