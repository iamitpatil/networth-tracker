package com.networth.controller;

import com.networth.model.entity.Form16Data;
import com.networth.model.entity.ItrFiling;
import com.networth.model.entity.User;
import com.networth.model.enums.TaxRegime;
import com.networth.repository.UserRepository;
import com.networth.service.DocumentService;
import com.networth.service.tax.CapitalGainsCalculator;
import com.networth.service.tax.DeductionService;
import com.networth.service.tax.Form16Service;
import com.networth.service.tax.ItrFilingService;
import com.networth.service.tax.TaxHarvestService;
import com.networth.service.tax.TaxRegimeCalculator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
public class TaxController {

    private final CapitalGainsCalculator capitalGainsCalculator;
    private final TaxHarvestService taxHarvestService;
    private final DeductionService deductionService;
    private final TaxRegimeCalculator taxRegimeCalculator;
    private final Form16Service form16Service;
    private final ItrFilingService itrFilingService;
    private final UserRepository userRepository;
    private final DocumentService documentService;

    // ===== Existing Endpoints =====

    @GetMapping("/summary/{financialYear}")
    public ResponseEntity<Map<String, Object>> getTaxSummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String financialYear) {
        return ResponseEntity.ok(capitalGainsCalculator.calculateCapitalGains(
                UUID.fromString(userDetails.getUsername()), financialYear));
    }

    @GetMapping("/capital-gains/{financialYear}")
    public ResponseEntity<Map<String, Object>> getCapitalGains(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String financialYear) {
        return ResponseEntity.ok(capitalGainsCalculator.calculateCapitalGains(
                UUID.fromString(userDetails.getUsername()), financialYear));
    }

    @GetMapping("/harvesting-opportunities")
    public ResponseEntity<List<Map<String, Object>>> getHarvestingOpportunities(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "2024-2025") String financialYear) {
        return ResponseEntity.ok(taxHarvestService.findHarvestingOpportunities(
                UUID.fromString(userDetails.getUsername()), financialYear));
    }

    @GetMapping("/80c-utilization")
    public ResponseEntity<Map<String, Object>> get80CUtilization(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "2024-2025") String financialYear) {
        return ResponseEntity.ok(deductionService.get80CUtilization(
                UUID.fromString(userDetails.getUsername()), financialYear));
    }

    // ===== Tax Regime =====

    @GetMapping("/regime")
    public ResponseEntity<Map<String, Object>> getTaxRegime(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        return ResponseEntity.ok(Map.of(
                "regime", user.getTaxRegime() != null ? user.getTaxRegime() : TaxRegime.NEW,
                "supportedRegimes", List.of(TaxRegime.OLD, TaxRegime.NEW)
        ));
    }

    @PutMapping("/regime")
    public ResponseEntity<Map<String, Object>> updateTaxRegime(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {
        String regimeStr = body.get("regime");
        if (regimeStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "regime is required"));
        }
        TaxRegime regime;
        try {
            regime = TaxRegime.valueOf(regimeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid regime. Use OLD or NEW"));
        }

        User user = getUser(userDetails);
        user.setTaxRegime(regime);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("regime", regime));
    }

    @PostMapping("/regime/compare")
    public ResponseEntity<Map<String, Object>> compareRegimes(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> body) {
        BigDecimal grossSalary = toBigDecimal(body.get("grossSalary"));
        BigDecimal totalDeductions = toBigDecimal(body.get("totalDeductions"));
        BigDecimal hraExemption = toBigDecimal(body.get("hraExemption"));
        BigDecimal stdOld = toBigDecimal(body.get("standardDeductionOld"));
        BigDecimal stdNew = toBigDecimal(body.get("standardDeductionNew"));

        if (grossSalary == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "grossSalary is required"));
        }

        TaxRegimeCalculator.RegimeComparison comparison =
                taxRegimeCalculator.compareRegimes(grossSalary, totalDeductions, hraExemption, stdOld, stdNew);
        return ResponseEntity.ok(comparison.toMap());
    }

    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculateTax(
            @RequestBody Map<String, Object> body) {
        BigDecimal income = toBigDecimal(body.get("taxableIncome"));
        String regimeStr = (String) body.get("regime");
        if (income == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "taxableIncome is required"));
        }
        TaxRegime regime = TaxRegime.NEW;
        if (regimeStr != null) {
            try {
                regime = TaxRegime.valueOf(regimeStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        return ResponseEntity.ok(taxRegimeCalculator.calculateTax(income, regime).toMap());
    }

    // ===== Form 16 =====

    @GetMapping("/form16")
    public ResponseEntity<List<Form16Data>> getAllForm16s(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(form16Service.getUserForm16s(UUID.fromString(userDetails.getUsername())));
    }

    @GetMapping("/form16/{financialYear}")
    public ResponseEntity<?> getForm16(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String financialYear) {
        return form16Service.getForUserAndYear(
                UUID.fromString(userDetails.getUsername()), financialYear)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/form16/upload")
    public ResponseEntity<?> uploadForm16(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String financialYear) {
        try {
            UUID userId = UUID.fromString(userDetails.getUsername());

            // First, save the PDF as a document
            var doc = documentService.uploadDocument(
                    userId, file, "FORM_16", "Form 16 - " + (financialYear != null ? financialYear : "Auto-detected"),
                    null, null, null, null, null, null);

            // Then parse the Form 16
            Form16Data form16 = form16Service.parseAndSave(userId, file, financialYear);

            // Link document to Form 16
            form16.setDocumentId(doc.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "form16", form16,
                    "documentId", doc.getId().toString(),
                    "parseConfidence", form16.getParseConfidence()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to parse Form 16: " + e.getMessage()));
        }
    }

    @PostMapping("/form16")
    public ResponseEntity<Form16Data> createManualForm16(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody Form16Data data) {
        Form16Data saved = form16Service.saveManual(UUID.fromString(userDetails.getUsername()), data);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/form16/{id}")
    public ResponseEntity<Void> deleteForm16(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        form16Service.delete(UUID.fromString(userDetails.getUsername()), UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    // ===== ITR Filings =====

    @GetMapping("/itr")
    public ResponseEntity<List<ItrFiling>> getAllItrFilings(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(itrFilingService.getUserItrFilings(UUID.fromString(userDetails.getUsername())));
    }

    @GetMapping("/itr/{financialYear}")
    public ResponseEntity<List<ItrFiling>> getItrByYear(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String financialYear) {
        return ResponseEntity.ok(itrFilingService.getByYear(
                UUID.fromString(userDetails.getUsername()), financialYear));
    }

    @PostMapping("/itr")
    public ResponseEntity<ItrFiling> createItrFiling(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ItrFiling filing) {
        ItrFiling saved = itrFilingService.save(UUID.fromString(userDetails.getUsername()), filing);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/itr/upload")
    public ResponseEntity<?> uploadItrPdf(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String financialYear,
            @RequestParam(required = false) String acknowledgementNumber,
            @RequestParam(required = false) String itrFormType,
            @RequestParam(required = false) String filingType) {
        try {
            UUID userId = UUID.fromString(userDetails.getUsername());

            // Save PDF as a document
            String desc = "ITR Filing" + (financialYear != null ? " - " + financialYear : "");
            var doc = documentService.uploadDocument(
                    userId, file, "ITR", desc,
                    null, null, null, null, null, null);

            // Create ITR record linked to the document
            ItrFiling filing = ItrFiling.builder()
                    .userId(userId)
                    .financialYear(financialYear != null ? financialYear : "UNKNOWN")
                    .acknowledgementNumber(acknowledgementNumber)
                    .itrFormType(itrFormType)
                    .filingType(filingType != null ? filingType : "ORIGINAL")
                    .documentId(doc.getId())
                    .source("ITR_PDF")
                    .build();

            ItrFiling saved = itrFilingService.save(userId, filing);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "itrFiling", saved,
                    "documentId", doc.getId().toString(),
                    "message", "ITR uploaded. Please complete the form details for record-keeping."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload ITR: " + e.getMessage()));
        }
    }

    @PutMapping("/itr/{id}")
    public ResponseEntity<ItrFiling> updateItr(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @RequestBody ItrFiling updates) {
        ItrFiling updated = itrFilingService.update(
                UUID.fromString(userDetails.getUsername()),
                UUID.fromString(id), updates);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/itr/{id}")
    public ResponseEntity<Void> deleteItr(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        itrFilingService.delete(UUID.fromString(userDetails.getUsername()), UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    // ===== Helpers =====

    private User getUser(UserDetails userDetails) {
        return userRepository.findById(UUID.fromString(userDetails.getUsername()))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (value instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
