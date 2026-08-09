package com.networth.service.documentgraph.nodes;

import com.networth.model.dto.TransactionRequest;
import com.networth.model.enums.TransactionType;
import com.networth.service.SalaryService;
import com.networth.service.SpendAnalyticsService;
import com.networth.service.documentgraph.*;
import com.networth.service.portfolio.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionNode implements ProcessingNode {

    private final TransactionService transactionService;
    private final SpendAnalyticsService spendAnalyticsService;
    private final SalaryService salaryService;

    @Override
    public String getName() {
        return "execute";
    }

    @Override
    public void process(GraphState state) {
        List<ProposedAction> actions = state.getProposedActions();
        if (actions == null || actions.isEmpty()) {
            state.setExecutedActions(List.of());
            return;
        }

        String userId = state.getUserId().toString();
        List<ExecutedAction> results = new ArrayList<>();

        for (ProposedAction action : actions) {
            try {
                ExecutedAction result = executeAction(userId, action);
                results.add(result);
            } catch (Exception e) {
                log.warn("Failed to execute action {}: {}", action.getType(), e.getMessage());
                results.add(ExecutedAction.builder()
                        .action(action)
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build());
            }
        }

        state.setExecutedActions(results);
        log.debug("Executed {}/{} actions successfully",
                results.stream().filter(ExecutedAction::isSuccess).count(), results.size());
    }

    private ExecutedAction executeAction(String userId, ProposedAction action) {
        return switch (action.getType()) {
            case CREATE_TRANSACTION -> executeCreateTransaction(userId, action);
            case UPDATE_CC_SPEND -> executeUpdateCcSpend(userId, action);
            case UPDATE_SALARY -> executeUpdateSalary(userId, action);
            case UPDATE_ACCOUNT_BALANCE -> executeUpdateAccountBalance(userId, action);
            case UPDATE_USER_TAX_INFO -> executeUpdateUserTaxInfo(userId, action);
            default -> ExecutedAction.builder()
                    .action(action)
                    .success(true)
                    .result(Map.of("note", "Action type " + action.getType() + " not yet implemented"))
                    .build();
        };
    }

    private ExecutedAction executeCreateTransaction(String userId, ProposedAction action) {
        Map<String, Object> data = action.getData();
        List<Map<String, Object>> txns = (List<Map<String, Object>>) data.getOrDefault("transactions", List.of());
        UUID holdingId = action.getEntityId();

        if (holdingId == null) {
            return ExecutedAction.builder()
                    .action(action)
                    .success(false)
                    .errorMessage("No holding ID matched for transaction")
                    .build();
        }

        List<Map<String, Object>> created = new ArrayList<>();
        for (Map<String, Object> txn : txns) {
            try {
                String dateStr = (String) txn.getOrDefault("date", LocalDate.now().toString());
                LocalDateTime date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();

                Number amountNum = (Number) txn.getOrDefault("amount", 0);
                BigDecimal amount = BigDecimal.valueOf(amountNum.doubleValue());

                String typeStr = (String) txn.getOrDefault("type", "BUY");
                TransactionType txnType = TransactionType.BUY;
                try {
                    txnType = TransactionType.valueOf(typeStr.toUpperCase());
                } catch (IllegalArgumentException ignored) {}

                TransactionRequest request = TransactionRequest.builder()
                        .holdingId(holdingId.toString())
                        .transactionType(txnType)
                        .quantity(BigDecimal.ONE)
                        .price(amount)
                        .transactionDate(date)
                        .notes((String) txn.getOrDefault("description", ""))
                        .build();

                transactionService.addTransaction(userId, request);
                created.add(Map.of(
                        "date", dateStr,
                        "amount", amount,
                        "type", txnType.name(),
                        "status", "created"
                ));
            } catch (Exception e) {
                created.add(Map.of(
                        "transaction", txn,
                        "error", e.getMessage()
                ));
            }
        }

        return ExecutedAction.builder()
                .action(action)
                .success(true)
                .result(Map.of("transactionsCreated", created.size(), "details", created))
                .build();
    }

    private ExecutedAction executeUpdateCcSpend(String userId, ProposedAction action) {
        Map<String, Object> data = action.getData();
        try {
            UUID userIdUuid = UUID.fromString(userId);
            var report = spendAnalyticsService.saveReport(userIdUuid, data);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", report.getId().toString());
            result.put("statementDate", report.getStatementDate() != null ? report.getStatementDate().toString() : null);
            result.put("cardIssuer", report.getCardIssuer());
            result.put("newCharges", report.getNewCharges());
            result.put("totalAmountDue", report.getTotalAmountDue());
            return ExecutedAction.builder()
                    .action(action)
                    .success(true)
                    .result(result)
                    .build();
        } catch (Exception e) {
            return ExecutedAction.builder()
                    .action(action)
                    .success(false)
                    .errorMessage("Failed to save CC spend report: " + e.getMessage())
                    .build();
        }
    }

    private ExecutedAction executeUpdateSalary(String userId, ProposedAction action) {
        Map<String, Object> data = action.getData();
        try {
            UUID userIdUuid = UUID.fromString(userId);
            String employerName = (String) data.getOrDefault("employerName", "Unknown Employer");
            Number grossPay = (Number) data.getOrDefault("grossPay", 0);
            Number netPay = (Number) data.getOrDefault("netPay", 0);
            String payDateStr = (String) data.getOrDefault("payDate", LocalDate.now().toString());
            LocalDate payDate = LocalDate.parse(payDateStr, DateTimeFormatter.ISO_LOCAL_DATE);

            Object componentsObj = data.get("components");
            Map<String, Object> components = componentsObj instanceof Map ? (Map<String, Object>) componentsObj : Map.of();

            SalaryService.SalaryRequest req = new SalaryService.SalaryRequest(
                    employerName,
                    BigDecimal.valueOf(netPay.doubleValue()),
                    null,
                    payDate,
                    "Imported via document graph",
                    components,
                    null
            );

            var salary = salaryService.createSalary(userIdUuid, req);
            return ExecutedAction.builder()
                    .action(action)
                    .success(true)
                    .result(Map.of("salaryId", salary.getId().toString(), "employerName", employerName, "netPay", netPay))
                    .build();
        } catch (Exception e) {
            return ExecutedAction.builder()
                    .action(action)
                    .success(false)
                    .errorMessage("Failed to create salary: " + e.getMessage())
                    .build();
        }
    }

    private ExecutedAction executeUpdateAccountBalance(String userId, ProposedAction action) {
        // Stub — account balance updates require bank account service integration
        return ExecutedAction.builder()
                .action(action)
                .success(true)
                .result(Map.of("note", "Account balance update requires Phase 2 integration"))
                .build();
    }

    private ExecutedAction executeUpdateUserTaxInfo(String userId, ProposedAction action) {
        // Stub — tax info updates require user profile service integration
        return ExecutedAction.builder()
                .action(action)
                .success(true)
                .result(Map.of("note", "Tax info update requires Phase 2 integration"))
                .build();
    }
}
