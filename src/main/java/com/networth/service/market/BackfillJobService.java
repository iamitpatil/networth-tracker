package com.networth.service.market;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages background backfill jobs for all market data types.
 * Only one job can run at a time (singleton lock via AtomicBoolean).
 *
 * Covers: equity price history (Upstox), MF NAV history (AMFI), NPS NAV (npsnav.in),
 * and symbol refresh (NSE equities, AMFI MFs, NSE bonds).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackfillJobService {

    private final UpstoxHistoricalService upstoxHistoricalService;
    private final AmfiHistoricalService amfiHistoricalService;
    private final NpsNavService npsNavService;
    private final com.networth.service.SymbolService symbolService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, Object> status = new ConcurrentHashMap<>();

    /**
     * Start a full backfill job in background.
     * Returns false if a job is already running.
     */
    @Async
    public void startFullBackfill(UUID userId, int historyDays) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Backfill job already running, skipping");
            return;
        }

        status.clear();
        status.put("running", true);
        status.put("startedAt", LocalDateTime.now().toString());
        status.put("startedBy", userId.toString());
        status.put("currentStep", "symbols");
        status.put("steps", List.of("symbols", "equities", "mutual_funds", "nps"));
        status.put("completedSteps", new ArrayList<String>());

        try {
            // Step 1: Refresh symbols (equities + MFs + bonds)
            updateStep("symbols", "Refreshing symbol lists (NSE equities, AMFI mutual funds, NSE bonds)...");
            try {
                symbolService.refreshAll();
                completeStep("symbols", "Symbol lists refreshed");
            } catch (Exception e) {
                log.error("Symbol refresh failed: {}", e.getMessage());
                stepError("symbols", "Symbol refresh failed: " + e.getMessage());
            }

            if (!running.get()) { cancelled(); return; }

            // Cancellation check passed into inner loops for immediate abort
            java.util.function.Supplier<Boolean> cancelCheck = running::get;

            // Progress callback: updates status map with intra-step progress
            java.util.function.BiConsumer<String, String> progressCallback = (step, msg) -> {
                status.put("currentStepProgress", msg);
            };

            // Step 2: Backfill equity price history
            updateStep("equities", "Backfilling equity price history...");
            try {
                LocalDate to = LocalDate.now(MarketCalendar.ZONE);
                LocalDate from = to.minusDays(historyDays);
                int count = upstoxHistoricalService.backfillAll(from, to, cancelCheck,
                        (processed, total, records, skipped) -> {
                            status.put("currentStepProgress",
                                    String.format("%d/%d symbols · %d records · %d skipped", processed, total, records, skipped));
                        });
                if (!running.get()) { cancelled(); return; }
                status.remove("currentStepProgress");
                completeStep("equities", count + " equity price records backfilled");
                status.put("equityRecords", count);
            } catch (Exception e) {
                if (!running.get()) { cancelled(); return; }
                log.error("Equity backfill failed: {}", e.getMessage());
                stepError("equities", "Equity backfill failed: " + e.getMessage());
            }

            if (!running.get()) { cancelled(); return; }

            // Step 3: Backfill MF NAV history
            updateStep("mutual_funds", "Backfilling mutual fund NAV history...");
            try {
                LocalDate to = LocalDate.now(MarketCalendar.ZONE);
                LocalDate from = to.minusDays(historyDays);
                int count = amfiHistoricalService.backfillAll(from, to, cancelCheck,
                        (processedDays, totalDaysVal, records) -> {
                            status.put("currentStepProgress",
                                    String.format("Day %d/%d · %d records", processedDays, totalDaysVal, records));
                        });
                if (!running.get()) { cancelled(); return; }
                status.remove("currentStepProgress");
                completeStep("mutual_funds", count + " MF NAV records backfilled");
                status.put("mfRecords", count);
            } catch (Exception e) {
                if (!running.get()) { cancelled(); return; }
                log.error("MF backfill failed: {}", e.getMessage());
                stepError("mutual_funds", "MF backfill failed: " + e.getMessage());
            }

            if (!running.get()) { cancelled(); return; }

            // Step 4: Refresh NPS NAVs
            updateStep("nps", "Refreshing NPS NAV values...");
            try {
                npsNavService.refreshSchemes();
                npsNavService.refreshAllNpsValues();
                completeStep("nps", "NPS NAVs refreshed");
            } catch (Exception e) {
                log.error("NPS refresh failed: {}", e.getMessage());
                stepError("nps", "NPS refresh failed: " + e.getMessage());
            }

            status.put("completedAt", LocalDateTime.now().toString());
            status.put("currentStep", "done");
            log.info("Full backfill job completed");
        } catch (Exception e) {
            log.error("Backfill job failed unexpectedly: {}", e.getMessage());
            status.put("error", e.getMessage());
        } finally {
            status.put("running", false);
            running.set(false);
        }
    }

    /**
     * Try to start a backfill. Returns true if started, false if already running.
     */
    public boolean tryStart(UUID userId, int historyDays) {
        if (running.get()) return false;
        startFullBackfill(userId, historyDays);
        return true;
    }

    /**
     * Cancel the running job (cooperative — checked between steps).
     */
    public boolean cancel() {
        if (!running.get()) return false;
        running.set(false);
        status.put("cancelled", true);
        return true;
    }

    /**
     * Get current job status.
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> result = new LinkedHashMap<>(status);
        result.putIfAbsent("running", false);
        return result;
    }

    public boolean isRunning() {
        return running.get();
    }

    private void updateStep(String step, String message) {
        status.put("currentStep", step);
        status.put("currentStepMessage", message);
        log.info("Backfill step: {} — {}", step, message);
    }

    @SuppressWarnings("unchecked")
    private void completeStep(String step, String message) {
        List<String> completed = (List<String>) status.getOrDefault("completedSteps", new ArrayList<>());
        completed.add(step);
        status.put("completedSteps", completed);
        status.put("currentStepMessage", message);
        Map<String, String> stepResults = (Map<String, String>) status.computeIfAbsent("stepResults", k -> new ConcurrentHashMap<>());
        stepResults.put(step, message);
        log.info("Backfill step completed: {} — {}", step, message);
    }

    @SuppressWarnings("unchecked")
    private void stepError(String step, String message) {
        Map<String, String> stepErrors = (Map<String, String>) status.computeIfAbsent("stepErrors", k -> new ConcurrentHashMap<>());
        stepErrors.put(step, message);
        // Don't stop the whole job — continue to next step
        List<String> completed = (List<String>) status.getOrDefault("completedSteps", new ArrayList<>());
        completed.add(step + " (error)");
        status.put("completedSteps", completed);
    }

    private void cancelled() {
        status.put("running", false);
        status.put("currentStep", "cancelled");
        status.put("currentStepMessage", "Job cancelled by user");
        status.put("completedAt", LocalDateTime.now().toString());
        log.info("Backfill job cancelled");
    }
}
