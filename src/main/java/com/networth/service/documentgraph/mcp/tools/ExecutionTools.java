package com.networth.service.documentgraph.mcp.tools;

import com.networth.model.dto.TransactionRequest;
import com.networth.model.enums.TransactionType;
import com.networth.service.BankAccountService;
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
    private final BankAccountService bankAccountService;

    @Tool(name = "create_transaction", description = "Create an investment transaction (buy/sell/dividend) for a holding. Provide quantity+price for stock trades, or just amount for lump sums.")
    public Map<String, Object> createTransaction(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Holding ID (UUID)") String holdingId,
            @ToolParam(description = "Transaction type: BUY, SELL, DIVIDEND, SIP, LUMPSUM") String type,
            @ToolParam(description = "Total transaction amount in INR") Number amount,
            @ToolParam(description = "Transaction date (YYYY-MM-DD)") String date,
            @ToolParam(description = "Optional description") String description) {
        try {
            LocalDateTime txnDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            TransactionType txnType;
            try { txnType = TransactionType.valueOf(type.toUpperCase()); }
            catch (IllegalArgumentException e) {
                log.warn("Unknown transaction type '{}', defaulting to BUY", type);
                txnType = TransactionType.BUY;
            }

            BigDecimal totalAmount = BigDecimal.valueOf(amount.doubleValue());
            // For AI-driven transactions where only total amount is known,
            // set quantity=1 and price=totalAmount. The amount field will be set
            // explicitly so TransactionService uses it directly.
            TransactionRequest request = TransactionRequest.builder()
                    .holdingId(holdingId).transactionType(txnType)
                    .quantity(BigDecimal.ONE).price(totalAmount)
                    .amount(totalAmount)
                    .transactionDate(txnDate).notes(description != null ? description : "Imported via AI")
                    .build();

            transactionService.addTransaction(userId, request);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "created");
            result.put("holdingId", holdingId);
            result.put("amount", amount);
            result.put("type", txnType.name());
            return result;
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Failed to create transaction: {}", errMsg);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "error");
            err.put("error", errMsg);
            return err;
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
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Failed to save CC spend report: {}", errMsg);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "error");
            err.put("error", errMsg);
            return err;
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
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "error");
            err.put("error", errMsg);
            return err;
        }
    }

    @Tool(name = "update_holding", description = "Update an investment holding's details (e.g., name, sector, ISIN)")
    public Map<String, Object> updateHolding(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Holding ID (UUID)") String holdingId,
            @ToolParam(description = "Data to update (name, sector, isin)") Map<String, Object> data) {
        // Note: This updates metadata only, not quantity/price (those go through create_transaction)
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "not_implemented");
        result.put("note", "Holding metadata updates coming soon. Use create_transaction for buy/sell.");
        result.put("holdingId", holdingId);
        return result;
    }

    @Tool(name = "update_account_balance", description = "Update a bank account's current balance")
    public Map<String, Object> updateAccountBalance(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Account ID (UUID)") String accountId,
            @ToolParam(description = "New balance amount in INR") Number balance) {
        try {
            UUID uid = UUID.fromString(userId);
            UUID acctId = UUID.fromString(accountId);
            BigDecimal newBalance = BigDecimal.valueOf(balance.doubleValue());

            BankAccountService.BankAccountRequest req = new BankAccountService.BankAccountRequest(
                    null, null, null, null, null, null, newBalance);
            var updated = bankAccountService.updateBankAccount(uid, acctId, req);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "updated");
            result.put("accountId", accountId);
            result.put("bankName", updated.getBankName());
            result.put("newBalance", updated.getBalance());
            return result;
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Failed to update account balance: {}", errMsg);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "error");
            err.put("error", errMsg);
            return err;
        }
    }

    @Tool(name = "link_document", description = "Link an uploaded document to an entity (holding, account, card)")
    public Map<String, Object> linkDocument(
            @ToolParam(description = "Document ID (UUID)") String documentId,
            @ToolParam(description = "Entity type (HOLDING, CREDIT_CARD, BANK_ACCOUNT)") String entityType,
            @ToolParam(description = "Entity ID (UUID)") String entityId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "not_implemented");
        result.put("note", "Document linking coming soon.");
        return result;
    }
}
