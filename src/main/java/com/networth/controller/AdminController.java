package com.networth.controller;

import com.networth.service.market.StartupBackfillService;
import com.networth.service.market.UpstoxMfFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin/maintenance endpoints.
 * In production, these should be restricted to admin users only.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final StartupBackfillService startupBackfillService;
    private final UpstoxMfFetcher upstoxMfFetcher;

    /**
     * Manually trigger backfill of historical data.
     */
    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> triggerBackfill(
            @AuthenticationPrincipal UserDetails userDetails) {
        startupBackfillService.triggerManualBackfill();
        return ResponseEntity.ok(Map.of(
                "status", "triggered",
                "message", "Backfill running asynchronously. Check logs for progress."
        ));
    }

    /**
     * Get status of MF data cache.
     */
    @GetMapping("/mf-status")
    public ResponseEntity<Map<String, Object>> mfStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(Map.of(
                "cachedFunds", upstoxMfFetcher.getCachedFundCount(),
                "available", upstoxMfFetcher.isAvailable()
        ));
    }
}
