package com.networth.controller;

import com.networth.service.market.AmfiHistoricalService;
import com.networth.service.market.UpstoxHistoricalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final UpstoxHistoricalService historicalService;
    private final AmfiHistoricalService amfiHistoricalService;

    @PostMapping("/backfill-prices")
    public ResponseEntity<?> backfillPrices(
            @RequestParam(defaultValue = "2025-01-01") String fromDate,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().toString()}") String toDate) {
        try {
            LocalDate from = LocalDate.parse(fromDate);
            LocalDate to = LocalDate.parse(toDate);
            int count = historicalService.backfillAll(from, to);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "recordsBackfilled", count,
                    "fromDate", from.toString(),
                    "toDate", to.toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/backfill-mf-history")
    public ResponseEntity<?> backfillMfHistory(
            @RequestParam(defaultValue = "2025-01-01") String fromDate,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().toString()}") String toDate) {
        try {
            LocalDate from = LocalDate.parse(fromDate);
            LocalDate to = LocalDate.parse(toDate);
            amfiHistoricalService.triggerBackfill(from, to);
            return ResponseEntity.ok(Map.of(
                    "status", "started",
                    "message", "MF history backfill started in background",
                    "fromDate", from.toString(),
                    "toDate", to.toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/backfill-mf-status")
    public ResponseEntity<Map<String, Object>> backfillMfStatus() {
        return ResponseEntity.ok(amfiHistoricalService.getBackfillStatus());
    }
}
