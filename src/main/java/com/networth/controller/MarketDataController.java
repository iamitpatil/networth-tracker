package com.networth.controller;

import com.networth.model.entity.Holding;
import com.networth.model.entity.Symbol;
import com.networth.repository.HoldingRepository;
import com.networth.repository.SymbolRepository;
import com.networth.service.market.AmfiHistoricalService;
import com.networth.service.market.BackfillJobService;
import com.networth.service.market.UpstoxHistoricalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final UpstoxHistoricalService historicalService;
    private final AmfiHistoricalService amfiHistoricalService;
    private final BackfillJobService backfillJobService;
    private final HoldingRepository holdingRepository;
    private final SymbolRepository symbolRepository;
    private final com.networth.service.FamilyService familyService;

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

    /**
     * Backfills price history for one holding's symbol, on demand when its chart is opened.
     *
     * <p>Requires the caller to own the holding, or to share an approved family with its owner --
     * the chart is reachable from the family view. Previously any holding UUID was accepted, and
     * because the method writes a resolved ISIN back to the row, that let one user modify
     * another's holding rather than merely read it.
     */
    @PostMapping("/backfill-holding/{holdingId}")
    public ResponseEntity<?> backfillHolding(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String holdingId,
            @RequestParam(defaultValue = "365") int days) {
        try {
            Holding holding = holdingRepository.findById(UUID.fromString(holdingId))
                    .orElseThrow(() -> new IllegalArgumentException("Holding not found"));

            UUID viewerId = UUID.fromString(userDetails.getUsername());
            if (!holding.getUserId().equals(viewerId)
                    && !familyService.getApprovedMemberIds(viewerId).contains(holding.getUserId())) {
                // Indistinguishable from a missing holding on purpose: a separate "forbidden"
                // would still confirm the holding exists.
                throw new IllegalArgumentException("Holding not found");
            }

            // Resolve ISIN if missing
            String isin = holding.getIsin();
            if (isin == null || isin.isBlank()) {
                Optional<Symbol> sym = symbolRepository.findById(holding.getSymbol());
                if (sym.isPresent() && sym.get().getIsin() != null && !sym.get().getIsin().isBlank()) {
                    isin = sym.get().getIsin();
                    holding.setIsin(isin);
                    holdingRepository.save(holding);
                }
            }

            if (isin == null || isin.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "No ISIN found for " + holding.getSymbol() + ". Cannot fetch price history."));
            }

            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(days);
            int count = historicalService.backfillSymbol(holding.getSymbol(), isin, from, to);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "recordsBackfilled", count,
                    "symbol", holding.getSymbol(),
                    "isin", isin));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
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

    // ── Full Backfill Job (async, singleton) ──

    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> startBackfill(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "365") int days) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        boolean started = backfillJobService.tryStart(userId, days);
        if (!started) {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("status", "already_running");
            body.put("message", "A backfill job is already running. Check status or wait for it to complete.");
            return ResponseEntity.status(409).body(body);
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("status", "started");
        body.put("message", "Full backfill started (symbols + equity prices + MF NAVs + NPS)");
        body.put("days", days);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/backfill/status")
    public ResponseEntity<Map<String, Object>> backfillStatus() {
        return ResponseEntity.ok(backfillJobService.getStatus());
    }

    @PostMapping("/backfill/cancel")
    public ResponseEntity<Map<String, Object>> cancelBackfill() {
        boolean cancelled = backfillJobService.cancel();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("cancelled", cancelled);
        body.put("message", cancelled ? "Backfill job cancellation requested" : "No backfill job is running");
        return ResponseEntity.ok(body);
    }
}
