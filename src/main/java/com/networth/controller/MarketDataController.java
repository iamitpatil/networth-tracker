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
    private final com.networth.service.market.provider.ProviderRateLimiter rateLimiter;
    private final com.networth.service.market.provider.ProviderRateLimits rateLimits;
    private final com.networth.service.market.SymbolEventService symbolEventService;
    /** {@code AsyncConfig}'s security-propagating executor, the same one the backfill job uses. */
    private final java.util.concurrent.Executor asyncExecutor;
    private final com.networth.service.market.provider.MarketDataResolver resolver;

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

    /**
     * Every market data provider's rate limit and how much of it is currently used.
     *
     * <p>Reports the figure the provider publishes next to the budget this application enforces,
     * which is intentionally lower. Without this the only way to find out why a price stopped
     * refreshing was to read the logs — and the answer is often simply that a provider's daily
     * quota is spent. Alpha Vantage's free tier is 25 requests <em>per day</em>, so that happens.
     */
    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> providerLimits() {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("enabled", rateLimits.isEnabled());
        body.put("maxWaitMs", rateLimits.getMaxWait().toMillis());
        body.put("chains", java.util.Arrays.stream(
                        com.networth.service.market.provider.MarketDataType.values())
                .collect(java.util.stream.Collectors.toMap(
                        Enum::name,
                        type -> resolver.getProviders(type).stream()
                                .map(com.networth.service.market.provider.MarketDataProvider::getName)
                                .toList(),
                        (a, b) -> a,
                        java.util.LinkedHashMap::new)));
        body.put("providers", rateLimiter.snapshot());
        return ResponseEntity.ok(body);
    }

    // ── Full Backfill Job (async, singleton) ──

    /**
     * Fetch and store corporate-action events on their own, without a full backfill.
     *
     * <p>The sync used to be reachable only as the last step of {@code POST /market/backfill}, behind an NPS
     * history step that re-fetches all 282 schemes every run and was measured at six hours to write zero
     * rows. Picking up a newly announced bonus should not require waiting that out.
     */
    @PostMapping("/events/sync")
    public ResponseEntity<Map<String, Object>> syncEvents() {
        boolean started = symbolEventService.startAsync(asyncExecutor);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("status", started ? "started" : "already_running");
        body.put("message", started
                ? "Corporate-action sync started. NSE allows ten requests a minute, so a full run over every "
                  + "listed symbol takes hours; held symbols are done first. Poll /market/events/status."
                : "A corporate-action sync is already running.");
        return ResponseEntity.status(started ? 200 : 409).body(body);
    }

    @GetMapping("/events/status")
    public ResponseEntity<Map<String, Object>> eventSyncStatus() {
        return ResponseEntity.ok(symbolEventService.status());
    }

    @PostMapping("/events/cancel")
    public ResponseEntity<Map<String, Object>> cancelEventSync() {
        boolean cancelled = symbolEventService.cancel();
        return ResponseEntity.ok(Map.of("cancelled", cancelled));
    }

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
        body.put("message", "Full backfill started (symbols + equity prices + MF NAVs + NPS + dividend events)");
        // days=0 means "from inception" rather than "from today", which is the only way to ask for a
        // stock's whole series; reported back so the caller can see which reading was taken.
        body.put("days", days);
        body.put("historyFrom", days <= 0 ? "inception" : days + " days ago");
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
