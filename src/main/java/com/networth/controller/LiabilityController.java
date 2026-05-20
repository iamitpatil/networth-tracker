package com.networth.controller;

import com.networth.model.entity.Liability;
import com.networth.service.CreditCardBillParser;
import com.networth.service.DocumentService;
import com.networth.service.EMIService;
import com.networth.service.EMIService.EMIScheduleEntry;
import com.networth.service.EMIService.LiabilityRequest;
import com.networth.service.FamilyDataService;
import com.networth.service.SpendAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/liabilities")
@RequiredArgsConstructor
public class LiabilityController {

    private final EMIService emiService;
    private final FamilyDataService familyDataService;
    private final CreditCardBillParser creditCardBillParser;
    private final DocumentService documentService;
    private final SpendAnalyticsService spendAnalyticsService;

    @GetMapping
    public ResponseEntity<List<Liability>> getUserLiabilities(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Map<String, String> params) {
        UUID uid = UUID.fromString(userDetails.getUsername());
        boolean fam = "f".equals(params.getOrDefault("view", ""));
        return ResponseEntity.ok(familyDataService.getLiabilities(uid, fam));
    }

    @PostMapping
    public ResponseEntity<Liability> createLiability(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody LiabilityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(emiService.createLiability(UUID.fromString(userDetails.getUsername()), request));
    }

    @GetMapping("/{id}/emi-schedule")
    public ResponseEntity<List<EMIScheduleEntry>> getEMISchedule(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        return ResponseEntity.ok(emiService.generateEMISchedule(
                UUID.fromString(userDetails.getUsername()),
                UUID.fromString(id)));
    }

    @PostMapping("/{id}/emi-pay")
    public ResponseEntity<Liability> markEMIPaid(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        LocalDate paymentDate = request.get("paymentDate") != null
                ? LocalDate.parse(request.get("paymentDate").toString())
                : LocalDate.now();
        BigDecimal amount = request.get("amount") != null
                ? new BigDecimal(request.get("amount").toString())
                : null;
        return ResponseEntity.ok(emiService.markEMIPaid(
                UUID.fromString(userDetails.getUsername()),
                UUID.fromString(id),
                paymentDate,
                amount));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<Map<String, Object>> getLoanSummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        return ResponseEntity.ok(emiService.getLoanSummary(
                UUID.fromString(userDetails.getUsername()),
                UUID.fromString(id)));
    }

    @PostMapping("/parse-cc-bill")
    public ResponseEntity<Map<String, Object>> parseCreditCardBill(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password) {
        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            var doc = documentService.uploadDocument(userId, file, "CC_BILL",
                    "Credit card bill upload", null, null, null);
            Map<String, Object> parsed = creditCardBillParser.parseBill(file, userDetails.getUsername(), password);
            Map<String, Object> result = new HashMap<>(parsed);
            result.put("documentId", doc.getId().toString());
            return ResponseEntity.ok(result);
        } catch (CreditCardBillParser.PasswordRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "PASSWORD_REQUIRED", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to parse credit card bill", "message", e.getMessage()));
        }
    }

    // ── Credit Card Spend Analytics ──

    @PostMapping("/cc-spend/save")
    public ResponseEntity<Map<String, Object>> saveCcSpendReport(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> parsedBill) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        var report = spendAnalyticsService.saveReport(userId, parsedBill);
        return ResponseEntity.ok(Map.of("id", report.getId(), "month", report.getStatementMonth(), "saved", true));
    }

    @GetMapping("/cc-spend/reports")
    public ResponseEntity<List<com.networth.model.entity.CcSpendReport>> getCcSpendReports(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(spendAnalyticsService.getUserReports(userId));
    }

    @GetMapping("/cc-spend/trend")
    public ResponseEntity<List<Map<String, Object>>> getCcSpendTrend(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(spendAnalyticsService.getMonthlyTrend(userId));
    }

    @GetMapping("/cc-spend/month/{month}")
    public ResponseEntity<Map<String, Object>> getCcMonthAnalysis(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String month) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(spendAnalyticsService.getMonthAnalysis(userId, month));
    }

    @GetMapping("/cc-spend/cards")
    public ResponseEntity<List<Map<String, String>>> getCcCards(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(spendAnalyticsService.getUserCards(userId));
    }

    @PostMapping("/cc-spend/{reportId}/pay")
    public ResponseEntity<Map<String, Object>> markBillPaid(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String reportId,
            @RequestBody Map<String, Object> request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        BigDecimal paidAmount = request.get("paidAmount") != null
                ? new BigDecimal(request.get("paidAmount").toString()) : null;
        LocalDate paidDate = request.get("paidDate") != null
                ? LocalDate.parse(request.get("paidDate").toString()) : null;
        String paymentMode = request.get("paymentMode") != null
                ? request.get("paymentMode").toString() : null;

        var report = spendAnalyticsService.markBillPaid(userId, UUID.fromString(reportId), paidAmount, paidDate, paymentMode);
        return ResponseEntity.ok(Map.of(
                "id", report.getId(),
                "paid", true,
                "paidAmount", report.getPaidAmount(),
                "paidDate", report.getPaidDate().toString(),
                "paymentMode", report.getPaymentMode()
        ));
    }

    @GetMapping("/cc-spend/payment-summary")
    public ResponseEntity<Map<String, Object>> getPaymentSummary(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(spendAnalyticsService.getPaymentSummary(userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLiability(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        emiService.deleteLiability(UUID.fromString(userDetails.getUsername()), UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }
}
