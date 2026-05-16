package com.networth.controller;

import com.networth.service.SIPCalendarService;
import com.networth.service.tax.CapitalGainsCalculator;
import com.networth.service.tax.DeductionService;
import com.networth.service.tax.TaxHarvestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/summary/{financialYear}")
    public ResponseEntity<Map<String, Object>> getTaxSummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String financialYear) {
        return ResponseEntity.ok(capitalGainsCalculator.calculateCapitalGains(UUID.fromString(userDetails.getUsername()), financialYear));
    }

    @GetMapping("/capital-gains/{financialYear}")
    public ResponseEntity<Map<String, Object>> getCapitalGains(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String financialYear) {
        return ResponseEntity.ok(capitalGainsCalculator.calculateCapitalGains(UUID.fromString(userDetails.getUsername()), financialYear));
    }

    @GetMapping("/harvesting-opportunities")
    public ResponseEntity<List<Map<String, Object>>> getHarvestingOpportunities(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "2024-2025") String financialYear) {
        return ResponseEntity.ok(taxHarvestService.findHarvestingOpportunities(UUID.fromString(userDetails.getUsername()), financialYear));
    }

    @GetMapping("/80c-utilization")
    public ResponseEntity<Map<String, Object>> get80CUtilization(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "2024-2025") String financialYear) {
        return ResponseEntity.ok(deductionService.get80CUtilization(UUID.fromString(userDetails.getUsername()), financialYear));
    }
}
