package com.networth.controller;

import com.networth.model.entity.Dividend;
import com.networth.service.DividendCalculationService;
import com.networth.service.DividendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dividends")
@RequiredArgsConstructor
public class DividendController {

    private final DividendService dividendService;
    private final DividendCalculationService dividendCalculationService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getDividendSummary(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(dividendService.getDividendSummary(UUID.fromString(userDetails.getUsername())));
    }

    @GetMapping("/passive-income")
    public ResponseEntity<Map<String, Object>> getPassiveIncomeEstimate(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(dividendService.getPassiveIncomeEstimate(UUID.fromString(userDetails.getUsername())));
    }

    /**
     * Trigger dividend calculation for all equity/ETF holdings.
     * Fetches dividend events from NSE/Yahoo, matches against transaction history,
     * computes payouts based on qty held on record date.
     */
    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculateDividends(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(dividendCalculationService.calculateDividends(userId));
    }

    /**
     * Get all dividend records for a specific holding.
     */
    @GetMapping("/holding/{holdingId}")
    public ResponseEntity<List<Dividend>> getHoldingDividends(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String holdingId) {
        return ResponseEntity.ok(dividendCalculationService.getHoldingDividends(UUID.fromString(holdingId)));
    }
}
