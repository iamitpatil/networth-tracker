package com.networth.service.documentgraph.mcp.tools;

import com.networth.model.dto.TransactionRequest;
import com.networth.model.enums.TransactionType;
import com.networth.service.SalaryService;
import com.networth.service.SpendAnalyticsService;
import com.networth.service.portfolio.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionTools {

    private final TransactionService transactionService;
    private final SpendAnalyticsService spendAnalyticsService;
    private final SalaryService salaryService;

    @Tool(name = "create_transaction", description = "Create an investment transaction (buy/sell/dividend) for a holding")
    public Map<String, Object> createTransaction(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Holding ID (UUID)") String holdingId,
            @ToolParam(description = "Transaction type: BUY, SELL, DIVIDEND, SIP, LUMPSUM") String type,
            @ToolParam(description = "Transaction amount/price") Number amount,
            @ToolParam(description = "Transaction date (YYYY-MM-DD)") String date,
            @ToolParam(description = "Optional description") String description) {
        try {
            LocalDateTime txnDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            TransactionType txnType;
            try { txnType = TransactionType.valueOf(type.toUpperCase()); }
            catch (IllegalArgumentException e) { txnType = TransactionType.BUY; }

            TransactionRequest request = TransactionRequest.builder()
                    .holdingId(holdingId).transactionType(txnType)
                    .quantity(BigDecimal.ONE).price(BigDecimal.valueOf(amount.doubleValue()))
                    .transactionDate(txnDate).notes(description != null ? description : "")
                    .build();

            transactionService.addTransaction(userId, request);
            return Map.of("status", "created", "holdingId", holdingId, "amount", amount, "type", txnType.name());
        } catch (Exception e) {
            log.warn("Failed to create transaction: {}", e.getMessage());
            return Map.of("status", "error", "error", e.getMessage());
        }
    }

    @Tool(name = "update_cc_spend", description = "Save or update a credit card spend report from a parsed bill")
    public Map<String, Object> updateCcSpend(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Parsed credit card bill data (cardIssuer, dueDate, transactions, etc.)") Map<String, Object> billData) {
        try {
            UUID uid = UUID.fromString(userId);
            var report = spendAnalyticsService.saveReport(uid, billData);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", report.getId().toString());
            result.put("statementDate", report.getStatementDate() != null ? report.getStatementDate().toString() : null);
            result.put("cardIssuer", report.getCardIssuer());
            result.put("newCharges", report.getNewCharges());
            result.put("totalAmountDue", report.getTotalAmountDue());
            return result;
        } catch (Exception e) {
            log.warn("Failed to save CC spend report: {}", e.getMessage());
            return Map.of("status", "error", "error", e.getMessage());
        }
    }

    @Tool(name = "update_salary", description = "Record a new salary/payslip entry from parsed salary slip data")
    public Map<String, Object> updateSalary(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Employer name") String employerName,
            @ToolParam(description = "Net pay amount") Number netPay,
            @ToolParam(description = "Pay date (YYYY-MM-DD)") String payDate,
            @ToolParam(description = "Salary components map (optional)", required = false) Map<String, Object> components) {
        try {
            UUID uid = UUID.fromString(userId);
            LocalDate date = LocalDate.parse(payDate, DateTimeFormatter.ISO_LOCAL_DATE);
            Map<String, Object> comps = components != null ? components : Map.of();

            SalaryService.SalaryRequest req = new SalaryService.SalaryRequest(
                    employerName, BigDecimal.valueOf(netPay.doubleValue()),
                    null, date, "Imported via MCP tool", comps, null);

            var salary = salaryService.createSalary(uid, req);
            return Map.of("salaryId", salary.getId().toString(), "employerName", employerName, "netPay", netPay);
        } catch (Exception e) {
            return Map.of("status", "error", "error", e.getMessage());
        }
    }

    @Tool(name = "update_holding", description = "Update an investment holding's details (e.g., current value, notes)")
    public Map<String, Object> updateHolding(
            @ToolParam(description = "Holding ID (UUID)") String holdingId,
            @ToolParam(description = "Data to update") Map<String, Object> data) {
        return Map.of("status", "not_implemented", "note", "Holding update requires Phase 3 service integration");
    }

    @Tool(name = "update_account_balance", description = "Update a bank account's current balance")
    public Map<String, Object> updateAccountBalance(
            @ToolParam(description = "Account ID (UUID)") String accountId,
            @ToolParam(description = "New balance amount") Number balance) {
        return Map.of("status", "not_implemented", "note", "Account balance update requires Phase 3 service integration");
    }

    @Tool(name = "link_document", description = "Link an uploaded document to an entity (holding, account, card)")
    public Map<String, Object> linkDocument(
            @ToolParam(description = "Document ID (UUID)") String documentId,
            @ToolParam(description = "Entity type (HOLDING, CREDIT_CARD, BANK_ACCOUNT)") String entityType,
            @ToolParam(description = "Entity ID (UUID)") String entityId) {
        return Map.of("status", "not_implemented", "note", "Document linking requires Phase 3 service integration");
    }
}
