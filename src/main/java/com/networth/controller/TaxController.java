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
import com.networth.service.tax.rules.TaxRuleRegistry;
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
    private final TaxRuleRegistry taxRuleRegistry;
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
            @RequestParam(required = false) String financialYear) {
        return ResponseEntity.ok(taxHarvestService.findHarvestingOpportunities(
                UUID.fromString(userDetails.getUsername()), resolveFinancialYear(financialYear)));
    }

    @GetMapping("/80c-utilization")
    public ResponseEntity<Map<String, Object>> get80CUtilization(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String financialYear) {
        return ResponseEntity.ok(deductionService.get80CUtilization(
                UUID.fromString(userDetails.getUsername()), resolveFinancialYear(financialYear)));
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
        String financialYear = body.get("financialYear") != null
                ? String.valueOf(body.get("financialYear"))
                : taxRuleRegistry.currentFinancialYear();

        if (grossSalary == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "grossSalary is required"));
        }

        TaxRegimeCalculator.RegimeComparison comparison =
                taxRegimeCalculator.compareRegimes(grossSalary, totalDeductions, hraExemption, stdOld, stdNew, financialYear);
        Map<String, Object> response = new java.util.LinkedHashMap<>(comparison.toMap());
        response.put("financialYear", taxRuleRegistry.canonicalise(financialYear));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculateTax(
            @RequestBody Map<String, Object> body) {
        BigDecimal income = toBigDecimal(body.get("taxableIncome"));
        String regimeStr = (String) body.get("regime");
        String financialYear = body.get("financialYear") != null
                ? String.valueOf(body.get("financialYear"))
                : taxRuleRegistry.currentFinancialYear();
        if (income == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "taxableIncome is required"));
        }
        TaxRegime regime = TaxRegime.NEW;
        if (regimeStr != null) {
            try {
                regime = TaxRegime.valueOf(regimeStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        Map<String, Object> response = new java.util.LinkedHashMap<>(
                taxRegimeCalculator.calculateTax(income, regime, financialYear).toMap());
        response.put("financialYear", taxRuleRegistry.canonicalise(financialYear));
        return ResponseEntity.ok(response);
    }

    /**
     * The headline figures in force for a financial year.
     *
     * <p>Exists so the UI can show accurate rates and deductions instead of hardcoding them.
     * Tax.jsx previously pinned "12.5%", "20%", "Max Rs 1.5L" and "Std Deduction: Rs 75,000"
     * as literals, which are correct only for one year and silently wrong for the 26 others
     * the year selector now offers.
     */
    @GetMapping("/rules/{financialYear}")
    public ResponseEntity<Map<String, Object>> getRulesForYear(@PathVariable String financialYear) {
        var ruleSet = taxRuleRegistry.forFinancialYear(financialYear);
        var cg = ruleSet.capitalGains();

        Map<String, Object> regimes = new java.util.LinkedHashMap<>();
        ruleSet.regimes().forEach((regime, rules) -> {
            Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("standardDeduction", rules.standardDeduction());
            r.put("rebateThreshold", rules.rebate().incomeThreshold());
            r.put("maxRebate", rules.rebate().maxRebate());
            r.put("allowsDeductions", rules.allowsDeductions());
            r.put("slabs", rules.slabs().stream().map(slab -> {
                Map<String, Object> sl = new java.util.LinkedHashMap<>();
                sl.put("upTo", slab.upTo());
                sl.put("rate", slab.rate());
                return sl;
            }).toList());
            regimes.put(regime.name(), r);
        });

        Map<String, Object> capitalGains = new java.util.LinkedHashMap<>();
        capitalGains.put("equityLtcgRate", cg.ltcgRate());
        capitalGains.put("equityStcgRate", cg.stcgRate());
        capitalGains.put("equityLtcgExemption", cg.ltcgExemption());
        capitalGains.put("cryptoRate", cg.cryptoRate());
        capitalGains.put("otherAssetLtcgRate", cg.otherAssetLtcgRate());

        Map<String, Object> deductions = new java.util.LinkedHashMap<>();
        deductions.put("limit80C", ruleSet.deductions().limit80C());
        deductions.put("limit80CCD1B", ruleSet.deductions().limit80CCD1B());
        deductions.put("limit80DSelf", ruleSet.deductions().limit80DSelf());

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("financialYear", ruleSet.financialYear());
        body.put("verified", ruleSet.verified());
        body.put("note", ruleSet.note());
        body.put("cessRate", ruleSet.cessRate());
        body.put("capitalGains", capitalGains);
        body.put("deductions", deductions);
        body.put("regimes", regimes);
        return ResponseEntity.ok(body);
    }

    /**
     * Financial years we have tax rules for. The UI populates its year selector from this so
     * a user cannot pick a year we would compute with the wrong rates.
     *
     * <p>{@code unverified} lists years whose figures have not been checked against an
     * authoritative source, so the UI can caveat them rather than presenting every year with
     * equal confidence. {@code comparable} lists years where more than one regime existed —
     * before FY 2020-21 there was only the old regime, so a regime comparison is meaningless
     * and the UI should not offer it.
     */
    @GetMapping("/financial-years")
    public ResponseEntity<Map<String, Object>> getSupportedFinancialYears() {
        return ResponseEntity.ok(Map.of(
                "financialYears", taxRuleRegistry.supportedFinancialYears(),
                "current", taxRuleRegistry.currentFinancialYear(),
                "unverified", taxRuleRegistry.unverifiedFinancialYears(),
                "comparable", taxRuleRegistry.comparableFinancialYears()));
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

    /**
     * The requested financial year, defaulting to the one in progress.
     *
     * <p>Two endpoints previously hardcoded "2024-2025" as their default, which quietly
     * became a past year. Resolving it per request means the default cannot rot.
     */
    private String resolveFinancialYear(String requested) {
        return (requested == null || requested.isBlank())
                ? taxRuleRegistry.currentFinancialYear()
                : requested;
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
