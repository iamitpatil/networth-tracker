package com.networth.controller;

import com.networth.model.entity.Liability;
import com.networth.service.CreditCardBillParser;
import com.networth.service.DocumentService;
import com.networth.service.EMIService;
import com.networth.service.EMIService.EMIScheduleEntry;
import com.networth.service.EMIService.LiabilityRequest;
import com.networth.service.FamilyDataService;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLiability(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        emiService.deleteLiability(UUID.fromString(userDetails.getUsername()), UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }
}
