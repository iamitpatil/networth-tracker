package com.networth.service.market;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages background backfill jobs for all market data types.
 * Only one job can run at a time (singleton lock via AtomicBoolean).
 *
 * Covers: equity price history (Upstox), MF NAV history (AMFI), NPS NAV + day-wise NPS NAV history
 * (npsnav.in), and symbol refresh (NSE equities, NSE ETFs, AMFI MFs, NSE bonds, NPS schemes).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackfillJobService {

    private final UpstoxHistoricalService upstoxHistoricalService;
    private final AmfiHistoricalService amfiHistoricalService;
    private final NpsNavService npsNavService;
    private final NpsHistoricalService npsHistoricalService;
    private final SymbolEventService symbolEventService;
    private final com.networth.service.SymbolService symbolService;

    /**
     * Where a full history load starts, for {@code days=0}.
     *
     * <p>Upstox's NSE daily candles begin 2000-01-03, and a window starting before a stock listed comes
     * back empty rather than failing — so one date serves every symbol and no per-symbol listing date is
     * needed.
     *
     * <p>Held as a String and parsed on use rather than injected as a {@code LocalDate}: binding a date
     * through {@code @Value} depends on the conversion service having an ISO converter registered, and a
     * mistyped property would then fail at context startup rather than where it is read.
     */
    @org.springframework.beans.factory.annotation.Value("${app.market.history-inception:2000-01-03}")
    private String historyInception;

    /**
     * How far back {@code days=0} reaches for <em>mutual fund</em> NAVs, in days.
     *
     * <p>Separate from the equity inception date, and much shorter, because the two providers are not
     * comparable. Upstox serves ten years of daily candles in one request, so a full equity history is
     * three calls per symbol and finishes in seconds. AMFI's historical endpoint returns
     * <em>one day</em> per request ({@code AmfiHistoricalService.MAX_DAYS_PER_REQUEST = 1}) and that
     * service takes no rate-limiter slot, so reaching 2000 means 9,130 unthrottled HTTP requests against
     * a free public endpoint and about two and a half hours during which no later step runs. Measured:
     * a {@code days=0} run sat on "Day 201/9131" after two minutes.
     *
     * <p>Five years is a deliberate compromise, not a limitation of the data. Raise it if you need more,
     * knowing the cost is one request per extra day.
     */
    @org.springframework.beans.factory.annotation.Value("${app.market.mf-history-days:1825}")
    private int mfHistoryDays;

    /**
     * The same executor {@code @Async} uses, injected rather than woven in.
     *
     * <p>{@code @Async} on the job body did nothing: {@link #tryStart} called it on {@code this}, and
     * a self-invocation never passes through the proxy that implements the annotation. The whole
     * backfill therefore ran on the Tomcat worker thread that served the POST — measured at 3.4s
     * against an empty dev database, and a year of history for a real portfolio takes minutes of
     * rate-limited provider calls. The caller could not receive the {@code "started"} response until
     * the work it describes had already finished, which makes the status endpoint it is supposed to
     * poll pointless and holds an HTTP thread hostage for the duration.
     *
     * <p>This is {@code AsyncConfig}'s {@code DelegatingSecurityContextAsyncTaskExecutor}, which
     * matters: the job's steps run on behalf of the requesting user, and a bare executor would hand
     * them an anonymous {@code SecurityContext}. It is the only {@code Executor} bean in the context
     * — declaring one makes Boot's own {@code applicationTaskExecutor} back off — and the field name
     * matches the bean name, so it still resolves by name if a second one is ever added.
     */
    private final Executor asyncExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, Object> status = new ConcurrentHashMap<>();

    /**
     * Claim the single job slot and run a full backfill on a background thread.
     *
     * @return false if a job is already running, in which case nothing was started
     */
    public boolean tryStart(UUID userId, int historyDays) {
        // The claim is the check: a plain `running.get()` followed by a start lets two simultaneous
        // callers both pass the read and both begin.
        if (!running.compareAndSet(false, true)) {
            log.warn("Backfill job already running, skipping");
            return false;
        }
        // Published before returning, so the caller's next poll of /backfill/status sees this run
        // rather than the previous one's leftovers.
        status.clear();
        status.put("running", true);
        status.put("startedAt", Instant.now().toString());
        status.put("startedBy", userId.toString());
        status.put("currentStep", "symbols");
        status.put("steps", List.of("symbols", "equities", "mutual_funds", "nps", "nps_history",
                "dividend_events"));
        status.put("completedSteps", new ArrayList<String>());

        asyncExecutor.execute(() -> runFullBackfill(historyDays));
        return true;
    }

    /**
     * The job itself. Runs on a background thread with the slot already claimed by
     * {@link #tryStart}, and is responsible for releasing it.
     */
    private void runFullBackfill(int historyDays) {        try {
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
                LocalDate from = startDate(historyDays, to);
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
                // Not startDate(): AMFI costs one request per day, so days=0 gets a bounded window
                // rather than the equity inception date. See mfHistoryDays.
                LocalDate from = historyDays > 0 ? to.minusDays(historyDays) : to.minusDays(mfHistoryDays);
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

            if (!running.get()) { cancelled(); return; }

            // Step 5: Backfill day-wise NPS NAV history.
            //
            // Last on purpose, and it is the longest step by far: one request per scheme for every
            // listed scheme, paced at the npsnav budget of 1/s. Everything ahead of it finishes
            // first, so a user who cancels mid-way keeps the equity and MF history already written.
            // Step 4 above only stamps today's NAV onto each account; this is what gives a pension
            // fund a value on a past date, which is the whole reason the chart was flat at cost.
            updateStep("nps_history", "Backfilling NPS NAV history (one request per scheme, paced)...");
            try {
                int count = npsHistoricalService.backfillAll(cancelCheck,
                        (processedSchemes, totalSchemes, records) -> {
                            status.put("currentStepProgress",
                                    String.format("%d/%d schemes · %d records", processedSchemes, totalSchemes, records));
                        });
                if (!running.get()) { cancelled(); return; }
                status.remove("currentStepProgress");
                completeStep("nps_history", count + " NPS NAV history records backfilled");
                status.put("npsHistoryRecords", count);
            } catch (Exception e) {
                if (!running.get()) { cancelled(); return; }
                log.error("NPS history backfill failed: {}", e.getMessage());
                stepError("nps_history", "NPS history backfill failed: " + e.getMessage());
            }

            if (!running.get()) { cancelled(); return; }

            // Step 6: Fetch and store corporate-action events.
            //
            // Last, and at the default app.events.scope=all it is now the longest step: one NSE request
            // per listed equity and ETF, paced at NSE's 10/min, so roughly four hours for ~2,428 symbols.
            // It earns that once. symbols.events_synced_at means a completed symbol is never re-fetched,
            // and the work list puts held symbols first, so cancelling part-way still leaves everything
            // anybody is looking at covered.
            //
            // This is what makes dividends calculable at all. They previously called the provider once
            // per holding through the resolver's non-waiting path, which skips a throttled provider; nse
            // allows 1/s and yahoo 5/s, so a 24-holding loop drained both buckets in 165ms and most
            // holdings got no request at all -- silently, at debug level.
            updateStep("dividend_events", "Fetching corporate-action events (one request per symbol, paced)...");
            try {
                int count = symbolEventService.syncAll(cancelCheck,
                        (processedSymbols, totalSymbols, events) -> {
                            status.put("currentStepProgress",
                                    String.format("%d/%d symbols · %d events", processedSymbols, totalSymbols, events));
                        });
                if (!running.get()) { cancelled(); return; }
                status.remove("currentStepProgress");
                completeStep("dividend_events", count + " corporate-action events stored");
                status.put("dividendEvents", count);
            } catch (Exception e) {
                if (!running.get()) { cancelled(); return; }
                log.error("Dividend event sync failed: {}", e.getMessage());
                stepError("dividend_events", "Dividend event sync failed: " + e.getMessage());
            }

            status.put("completedAt", Instant.now().toString());
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

    /**
     * Where a history load starts, given the requested number of days.
     *
     * <p>{@code days=0} means "from inception" rather than "from today", which is the only way to ask for
     * a stock's whole series. Before this, the smallest start the API could express was one day ago and
     * the default 365 pinned every symbol to a rolling year.
     */
    private LocalDate startDate(int historyDays, LocalDate to) {
        if (historyDays > 0) {
            return to.minusDays(historyDays);
        }
        try {
            return LocalDate.parse(historyInception.trim());
        } catch (Exception e) {
            // A mistyped property must not silently become "today" and quietly fetch nothing.
            log.warn("app.market.history-inception is not an ISO date ('{}'); falling back to 2000-01-03",
                    historyInception);
            return LocalDate.of(2000, 1, 3);
        }
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
        status.put("completedAt", Instant.now().toString());
        log.info("Backfill job cancelled");
    }
}
