package com.networth.service;

import com.networth.model.entity.CcSpendReport;
import com.networth.repository.CcSpendReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpendAnalyticsService {

    private final CcSpendReportRepository reportRepo;

    /**
     * Save a parsed CC bill as a spend report. Upserts by (userId, cardLastFour, statementMonth).
     */
    @Transactional
    public CcSpendReport saveReport(UUID userId, Map<String, Object> parsedBill) {
        String cardLastFour = str(parsedBill.get("cardLastFourDigits"));
        String statementDateStr = str(parsedBill.get("statementDate"));
        String statementMonth = deriveMonth(statementDateStr);

        // Upsert — replace if same card + month already exists
        Optional<CcSpendReport> existing = (cardLastFour != null && statementMonth != null)
                ? reportRepo.findByUserIdAndCardLastFourAndStatementMonth(userId, cardLastFour, statementMonth)
                : Optional.empty();

        CcSpendReport report = existing.orElse(CcSpendReport.builder().userId(userId).build());
        report.setCardIssuer(str(parsedBill.get("cardIssuer")));
        report.setCardLastFour(cardLastFour);
        report.setCardType(str(parsedBill.get("cardType")));
        report.setStatementMonth(statementMonth != null ? statementMonth : YearMonth.now().toString());
        report.setStatementDate(parseDate(statementDateStr));
        report.setDueDate(parseDate(str(parsedBill.get("dueDate"))));
        report.setTotalAmountDue(decimal(parsedBill.get("totalAmountDue")));
        report.setMinimumAmountDue(decimal(parsedBill.get("minimumAmountDue")));
        report.setPreviousBalance(decimal(parsedBill.get("previousBalance")));
        report.setPaymentsReceived(decimal(parsedBill.get("paymentsReceived")));
        report.setNewCharges(decimal(parsedBill.get("newCharges")));
        report.setDocumentId(parsedBill.get("documentId") != null ? UUID.fromString(parsedBill.get("documentId").toString()) : null);

        // Store spend summary and transactions as JSONB
        if (parsedBill.get("spendSummary") instanceof Map<?, ?> m) {
            report.setSpendSummary(new HashMap<>((Map<String, Object>) m));
        }
        if (parsedBill.get("transactions") instanceof List<?> l) {
            report.setTransactions(l.stream()
                    .filter(i -> i instanceof Map)
                    .map(i -> new HashMap<>((Map<String, Object>) i))
                    .collect(Collectors.toList()));
        }

        report = reportRepo.save(report);
        log.info("Saved CC spend report: {} *{} for {}", report.getCardIssuer(), report.getCardLastFour(), report.getStatementMonth());
        return report;
    }

    /**
     * Get all spend reports for a user, newest first.
     */
    public List<CcSpendReport> getUserReports(UUID userId) {
        return reportRepo.findByUserIdOrderByStatementMonthDesc(userId);
    }

    /**
     * Get monthly spend totals for a user (for trend chart).
     */
    public List<Map<String, Object>> getMonthlyTrend(UUID userId) {
        List<Object[]> rows = reportRepo.findMonthlyTotalsByUserId(userId);
        return rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", r[0]);
            m.put("total", r[1]);
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * Get aggregated spend breakdown across all cards for a specific month.
     */
    public Map<String, Object> getMonthAnalysis(UUID userId, String month) {
        List<CcSpendReport> reports = reportRepo.findByUserIdOrderByStatementMonthDesc(userId)
                .stream().filter(r -> month.equals(r.getStatementMonth())).toList();

        if (reports.isEmpty()) return Map.of("month", month, "totalSpend", 0, "categories", Map.of(), "cards", List.of());

        BigDecimal totalSpend = reports.stream()
                .map(r -> r.getTotalAmountDue() != null ? r.getTotalAmountDue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Aggregate category spend across all cards
        Map<String, BigDecimal> aggCategories = new LinkedHashMap<>();
        List<Map<String, Object>> allTransactions = new ArrayList<>();
        for (CcSpendReport r : reports) {
            if (r.getSpendSummary() != null) {
                r.getSpendSummary().forEach((cat, val) -> {
                    BigDecimal amt = val instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
                    aggCategories.merge(cat, amt, BigDecimal::add);
                });
            }
            if (r.getTransactions() != null) {
                allTransactions.addAll(r.getTransactions());
            }
        }

        // Top merchants
        Map<String, BigDecimal> merchantTotals = new LinkedHashMap<>();
        for (Map<String, Object> tx : allTransactions) {
            String desc = str(tx.get("description"));
            BigDecimal amt = decimal(tx.get("amount"));
            if (desc != null && amt != null && amt.compareTo(BigDecimal.ZERO) > 0) {
                merchantTotals.merge(desc, amt, BigDecimal::add);
            }
        }
        List<Map<String, Object>> topMerchants = merchantTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(10)
                .map(e -> Map.<String, Object>of("merchant", e.getKey(), "amount", e.getValue()))
                .toList();

        // Per-card breakdown
        List<Map<String, Object>> cards = reports.stream().map(r -> {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("issuer", r.getCardIssuer());
            c.put("lastFour", r.getCardLastFour());
            c.put("totalDue", r.getTotalAmountDue());
            c.put("minimumDue", r.getMinimumAmountDue());
            c.put("dueDate", r.getDueDate());
            c.put("transactionCount", r.getTransactions() != null ? r.getTransactions().size() : 0);
            return c;
        }).toList();

        // Month-over-month change
        String prevMonth = YearMonth.parse(month).minusMonths(1).toString();
        List<CcSpendReport> prevReports = reportRepo.findByUserIdOrderByStatementMonthDesc(userId)
                .stream().filter(r -> prevMonth.equals(r.getStatementMonth())).toList();
        BigDecimal prevTotal = prevReports.stream()
                .map(r -> r.getTotalAmountDue() != null ? r.getTotalAmountDue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal momChange = prevTotal.compareTo(BigDecimal.ZERO) > 0
                ? totalSpend.subtract(prevTotal).multiply(BigDecimal.valueOf(100)).divide(prevTotal, 1, RoundingMode.HALF_UP)
                : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", month);
        result.put("totalSpend", totalSpend);
        result.put("previousMonthSpend", prevTotal);
        result.put("monthOverMonthChange", momChange);
        result.put("categories", aggCategories);
        result.put("topMerchants", topMerchants);
        result.put("cards", cards);
        result.put("transactionCount", allTransactions.size());
        return result;
    }

    /**
     * Get all distinct cards for a user.
     */
    public List<Map<String, String>> getUserCards(UUID userId) {
        return reportRepo.findDistinctCardsByUserId(userId).stream().map(r -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("lastFour", r[0] != null ? r[0].toString() : "");
            m.put("issuer", r[1] != null ? r[1].toString() : "");
            m.put("type", r[2] != null ? r[2].toString() : "");
            return m;
        }).toList();
    }

    /**
     * Mark a CC bill as paid. The paid amount flows into next month's previousBalance/paymentsReceived.
     */
    @Transactional
    public CcSpendReport markBillPaid(UUID userId, UUID reportId, BigDecimal paidAmount, LocalDate paidDate, String paymentMode) {
        CcSpendReport report = reportRepo.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));
        if (!report.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized");
        }

        report.setPaid(true);
        report.setPaidAmount(paidAmount != null ? paidAmount : report.getTotalAmountDue());
        report.setPaidDate(paidDate != null ? paidDate : LocalDate.now());
        report.setPaymentMode(paymentMode != null ? paymentMode : "ONLINE");

        final CcSpendReport savedReport = reportRepo.save(report);
        log.info("Marked CC bill as paid: {} *{} {} — ₹{} via {}",
                savedReport.getCardIssuer(), savedReport.getCardLastFour(),
                savedReport.getStatementMonth(), savedReport.getPaidAmount(), savedReport.getPaymentMode());

        // Update next month's report if it exists — set paymentsReceived
        String nextMonth = java.time.YearMonth.parse(savedReport.getStatementMonth()).plusMonths(1).toString();
        reportRepo.findByUserIdAndCardLastFourAndStatementMonth(userId, savedReport.getCardLastFour(), nextMonth)
                .ifPresent(nextReport -> {
                    nextReport.setPaymentsReceived(savedReport.getPaidAmount());
                    reportRepo.save(nextReport);
                    log.info("Updated next month {} paymentsReceived to ₹{}", nextMonth, savedReport.getPaidAmount());
                });

        return savedReport;
    }

    /**
     * Get payment summary — total paid vs total due across all months.
     */
    public Map<String, Object> getPaymentSummary(UUID userId) {
        List<CcSpendReport> all = reportRepo.findByUserIdOrderByStatementMonthDesc(userId);
        BigDecimal totalDue = all.stream()
                .map(r -> r.getTotalAmountDue() != null ? r.getTotalAmountDue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = all.stream()
                .filter(r -> Boolean.TRUE.equals(r.getPaid()))
                .map(r -> r.getPaidAmount() != null ? r.getPaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long paidCount = all.stream().filter(r -> Boolean.TRUE.equals(r.getPaid())).count();
        long unpaidCount = all.stream().filter(r -> !Boolean.TRUE.equals(r.getPaid())).count();

        // Current outstanding (unpaid bills)
        BigDecimal currentOutstanding = all.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getPaid()))
                .map(r -> r.getTotalAmountDue() != null ? r.getTotalAmountDue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Next due date
        LocalDate nextDue = all.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getPaid()) && r.getDueDate() != null)
                .map(CcSpendReport::getDueDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalBilled", totalDue);
        result.put("totalPaid", totalPaid);
        result.put("currentOutstanding", currentOutstanding);
        result.put("paidBills", paidCount);
        result.put("unpaidBills", unpaidCount);
        result.put("nextDueDate", nextDue);
        return result;
    }

    // --- helpers ---
    private String str(Object o) { return o != null ? o.toString().trim() : null; }
    private BigDecimal decimal(Object o) {
        if (o == null) return null;
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return null; }
    }
    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }
    private String deriveMonth(String dateStr) {
        if (dateStr == null) return null;
        try {
            LocalDate d = LocalDate.parse(dateStr);
            return YearMonth.from(d).toString();
        } catch (Exception e) { return null; }
    }
}
