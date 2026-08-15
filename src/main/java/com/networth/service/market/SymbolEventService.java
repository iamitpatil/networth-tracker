package com.networth.service.market;

import com.networth.repository.SymbolRepository;
import com.networth.service.market.provider.CorporateActionEvent;
import com.networth.service.market.provider.MarketDataResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Fetches each symbol's corporate-action events once and stores them.
 *
 * <p>This exists because dividends were never being calculated. {@code DividendCalculationService}
 * called the provider chain once per holding, inside a loop, through the resolver's non-waiting path —
 * which skips a throttled provider rather than pausing. That is right on the live price path and wrong
 * in a batch: {@code nse} allows 1/s and 10/min, {@code yahoo} 5/s, so a 24-holding loop drained both
 * per-second buckets inside 165ms and roughly eighteen symbols had no request issued for them at all.
 * The skip logs at debug, so the endpoint returned {@code newDividends: 0} and looked successful.
 *
 * <p>The fix is not to retry harder but to stop asking per calculation. One NSE corporate-actions call
 * returns a symbol's <em>entire</em> dividend history — eighteen to twenty events going back a decade —
 * so the natural unit of work is once per symbol, not once per calculation. After a sync, computing
 * payouts is a local join: no provider, no rate limit, nothing to skip.
 *
 * <p>Yahoo cannot share the load. It answers every request with an IP-level
 * {@code 429 Edge: Too Many Requests}, reproduced from outside the app entirely, so NSE's 10/min is the
 * real budget: about four hours to cover all 2,428 listed equities and ETFs, or a few minutes for a
 * held-only run. Once, either way — {@code symbols.events_synced_at} means a completed symbol is not
 * asked again. That is why this runs as a cancellable backfill job step and never on a request thread.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SymbolEventService {

    private final MarketDataResolver marketDataResolver;
    private final SymbolRepository symbolRepository;
    private final JdbcTemplate jdbcTemplate;

    /** The only event type the dividend chain supplies today. */
    public static final String TYPE_DIVIDEND = "DIVIDEND";

    /** Rows per JDBC batch, matching {@code SymbolService} and {@code NpsHistoricalService}. */
    private static final int BATCH_SIZE = 1000;

    /**
     * How long a symbol may be left before its events are re-fetched.
     *
     * <p>Not a cache TTL — the stored events do not expire. It only bounds how long a
     * <em>newly announced</em> event stays unnoticed. Companies announce a few times a year, so 30 days
     * costs one call per symbol per month and never delays a payout that has already been recorded.
     */
    @Value("${app.events.max-age:30d}")
    private Duration maxAge;

    /**
     * How long a sync waits for a provider slot before giving up on a symbol.
     *
     * <p>Has to exceed NSE's minute window: at 10/min the eleventh symbol in a run waits the better
     * part of a minute for a slot, and the 2s default at {@code ProviderRateLimits:62} would abandon it.
     */
    @Value("${app.events.provider-wait:90s}")
    private Duration providerWait;

    /**
     * Which symbols the sync covers: {@code all} listed equities and ETFs, or only {@code held} ones.
     *
     * <p>Defaults to {@code all}. That is about 2,428 symbols and, at NSE's ten requests a minute,
     * roughly four hours — but the watermark makes it a one-off: a symbol that has been synced is not
     * asked again until {@link #maxAge} passes, so a second run has almost nothing left to do. The
     * payoff is that a dividend history is already present the day a holding is created, rather than the
     * new holding reading zero until the next sync.
     *
     * <p>Set {@code held} for a fast dev loop or a small deployment; it finishes in minutes and covers
     * everything anybody can currently see.
     */
    @Value("${app.events.scope:all}")
    private String scope;

    /**
     * Upsert rather than {@code DO NOTHING}: NSE does revise an announced amount before the record
     * date, and a correction has to win. A re-run over unchanged data still writes nothing new, which is
     * what keeps the sync idempotent.
     *
     * <p>{@code record_date} is coalesced so a source that stops publishing it — Yahoo supplies only an
     * ex-date — cannot blank a date NSE already gave us.
     */
    private static final String UPSERT_SQL = """
            INSERT INTO symbol_events (symbol, event_type, event_subtype, amount_per_share, ratio,
                                       ex_date, record_date, description, source, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (symbol, ex_date, event_type, event_subtype) DO UPDATE
               SET amount_per_share = EXCLUDED.amount_per_share,
                   ratio            = COALESCE(EXCLUDED.ratio, symbol_events.ratio),
                   record_date      = COALESCE(EXCLUDED.record_date, symbol_events.record_date),
                   description      = EXCLUDED.description,
                   source           = EXCLUDED.source,
                   updated_at       = now()
            """;

    /** Progress during a long sync, shaped like the callbacks {@code BackfillJobService} already passes. */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int processedSymbols, int totalSymbols, int events);
    }

    /**
     * One sync at a time, so two callers cannot both spend the NSE budget on the same work list.
     *
     * <p>Claimed by {@link #startAsync} and released when the run ends.
     */
    private final java.util.concurrent.atomic.AtomicBoolean running =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Live status for the endpoint to poll, in the shape {@code BackfillJobService} already publishes. */
    private final Map<String, Object> status = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Run a sync on a background thread and return immediately.
     *
     * <p>Exists because the sync was only reachable as the last step of the full backfill, behind the NPS
     * history step — which re-fetches all 282 schemes on every run and took six hours to write zero rows.
     * Waiting six hours to pick up a newly announced bonus is not a usable way to reach this, and the two
     * pieces of work have nothing to do with each other.
     *
     * @return false if a sync is already running, in which case nothing was started
     */
    public boolean startAsync(java.util.concurrent.Executor executor) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        status.clear();
        status.put("running", true);
        status.put("startedAt", Instant.now().toString());
        executor.execute(() -> {
            try {
                int stored = syncAll(running::get, (processed, total, events) -> {
                    status.put("progress", processed + "/" + total + " symbols · " + events + " events");
                    status.put("processed", processed);
                    status.put("total", total);
                });
                status.put("eventsStored", stored);
            } catch (Exception e) {
                log.error("Event sync failed: {}", e.getMessage());
                status.put("error", e.getMessage());
            } finally {
                running.set(false);
                status.put("running", false);
                status.put("finishedAt", Instant.now().toString());
            }
        });
        return true;
    }

    /** Ask a running sync to stop at the next symbol. */
    public boolean cancel() {
        return running.compareAndSet(true, false);
    }

    public Map<String, Object> status() {
        return new LinkedHashMap<>(status);
    }

    // ── one symbol ────────────────────────────────────────────────────────────

    /**
     * Fetch and store one symbol's events.
     *
     * @return how many rows the store gained; 0 both for "already had them all" and for "the provider
     *         gave us nothing", which the log line distinguishes
     */
    public int syncSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return 0;
        }
        String key = symbol.trim();

        List<CorporateActionEvent> events;
        try {
            events = marketDataResolver.getCorporateActionsWaiting(key, providerWait);
        } catch (Exception e) {
            // Never fatal. One dead symbol must not take down the sync, and the watermark is left
            // unstamped below so the next run retries it.
            log.warn("Event sync failed for {}: {}", key, e.getMessage());
            return 0;
        }

        if (events == null) {
            // No provider was reachable — every one in the chain was throttled past the wait or threw.
            // Deliberately not stamped: this symbol must be retried, because marking it done would let a
            // provider outage masquerade as "this company pays no dividend" and leave the store
            // permanently empty. That silent-success shape is the whole reason this class exists.
            log.warn("No provider reachable for {}; leaving it unsynced so the next run retries", key);
            return 0;
        }

        if (events.isEmpty()) {
            // A provider answered and reported nothing. That is a real answer, not a failure — plenty of
            // listed companies have never declared a dividend — so it is stamped and not asked again
            // until max-age. At scope=all this matters: without the distinction above, several hundred
            // dividend-less symbols would be re-fetched on every run and, at ten a minute, eat hours.
            stampSynced(key);
            log.debug("{} has no announced corporate actions; marked synced", key);
            return 0;
        }

        List<Object[]> rows = toRows(key, events);
        if (rows.isEmpty()) {
            // The provider answered, but nothing it sent was storable — an event with no ex-date cannot be
            // placed in time and a zero payout is meaningless. Treated like an empty answer: stamped, and
            // not counted as a failure, because re-asking would return the same unusable rows.
            stampSynced(key);
            log.debug("{}: {} events returned, none usable; marked synced", key, events.size());
            return 0;
        }

        long before = countFor(key);
        upsert(rows);
        long added = countFor(key) - before;

        stampSynced(key);
        log.info("Synced {}: {} events from the provider, {} new rows stored", key, rows.size(), added);
        return (int) added;
    }

    // ── every held symbol that needs it ───────────────────────────────────────

    public int syncAll() {
        return syncAll(() -> true, null);
    }

    /**
     * Sync every equity or ETF whose events are missing or older than {@link #maxAge}.
     *
     * <p>Scope comes from {@code app.events.scope}, default {@code all}. A full first run is long — 2,428
     * symbols at ten a minute — so it is cancellable at every symbol and the work list puts held symbols
     * first, meaning an interrupted run has already covered whatever anybody is looking at.
     */
    public int syncAll(Supplier<Boolean> cancelCheck, ProgressCallback progress) {
        Instant before = Instant.now().minus(maxAge);
        boolean heldOnly = "held".equalsIgnoreCase(scope == null ? "" : scope.trim());
        List<String> targets = heldOnly
                ? symbolRepository.findHeldSymbolsNeedingEventSync(before)
                : symbolRepository.findAllSymbolsNeedingEventSync(before);
        if (targets.isEmpty()) {
            log.info("No symbols need an event sync (scope={})", heldOnly ? "held" : "all");
            return 0;
        }
        // The estimate is worth logging because the number is surprising: this is the one step that
        // takes hours rather than seconds, and it is NSE's 10/min budget that decides it, not us.
        log.info("Event sync starting for {} symbols (scope={}); NSE allows 10/min, so expect about {} min",
                targets.size(), heldOnly ? "held" : "all", Math.max(1, targets.size() / 10));

        int events = 0;
        int processed = 0;
        for (String symbol : targets) {
            if (!cancelCheck.get()) {
                log.info("Event sync cancelled at {}/{} symbols, {} events", processed, targets.size(), events);
                return events;
            }
            try {
                events += syncSymbol(symbol);
            } catch (Exception e) {
                log.warn("Event sync failed for {}: {}", symbol, e.getMessage());
            }
            processed++;
            if (progress != null) {
                progress.onProgress(processed, targets.size(), events);
            }
        }
        log.info("Event sync complete: {} symbols processed, {} new events stored", processed, events);
        return events;
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    /**
     * Provider events to insertable rows, deduplicated on the conflict key.
     *
     * <p>The dedupe is not tidiness. PostgreSQL rejects an {@code ON CONFLICT DO UPDATE} statement that
     * would touch the same row twice — "cannot affect row a second time" — so two provider entries
     * sharing a symbol, ex-date, type and subtype would abort the whole batch, taking every other event
     * for that symbol with them. Last one wins, which matches the upsert's intent that a correction
     * supersedes.
     *
     * <p>A dividend must carry a positive amount and a bonus or split a ratio; anything else is dropped
     * rather than stored as an event nobody can act on.
     */
    private List<Object[]> toRows(String symbol, List<CorporateActionEvent> events) {
        Map<String, Object[]> byKey = new LinkedHashMap<>();
        for (CorporateActionEvent e : events) {
            LocalDate exDate = e.getExDate() != null ? e.getExDate() : e.getRecordDate();
            if (exDate == null) {
                continue; // ex_date is NOT NULL, and an event with neither date cannot be placed in time
            }
            String type = e.getEventType();
            if (type == null || type.isBlank()) {
                continue;
            }
            BigDecimal amount = e.getAmountPerShare();
            BigDecimal ratio = e.getRatio();
            boolean usable = TYPE_DIVIDEND.equals(type)
                    ? amount != null && amount.signum() > 0
                    // A demerger legitimately has no ratio: its entitlement relates two companies, and the
                    // multiplier belongs to that pairing rather than to this row.
                    : ratio != null || "DEMERGER".equals(type);
            if (!usable) {
                continue;
            }
            String subtype = e.getEventSubtype() == null ? "" : truncate(e.getEventSubtype(), 20);
            byKey.put(exDate + "|" + type + "|" + subtype, new Object[]{
                    symbol,
                    truncate(type, 20),
                    subtype,
                    amount,
                    ratio,
                    exDate,
                    e.getRecordDate(),
                    truncate(e.getDescription(), 500),
                    truncate(e.getSource() != null ? e.getSource() : "UNKNOWN", 20),
            });
        }
        return new ArrayList<>(byKey.values());
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    // ── persistence ───────────────────────────────────────────────────────────

    private void upsert(List<Object[]> rows) {
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<Object[]> chunk = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Object[] row = chunk.get(i);
                    ps.setString(1, (String) row[0]);
                    ps.setString(2, (String) row[1]);
                    ps.setString(3, (String) row[2]);
                    ps.setBigDecimal(4, (BigDecimal) row[3]);
                    setNullableDecimal(ps, 5, (BigDecimal) row[4]);
                    ps.setDate(6, Date.valueOf((LocalDate) row[5]));
                    setNullableDate(ps, 7, (LocalDate) row[6]);
                    ps.setString(8, (String) row[7]);
                    ps.setString(9, (String) row[8]);
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
    }

    private void setNullableDate(PreparedStatement ps, int index, LocalDate value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setDate(index, Date.valueOf(value));
        }
    }

    private void setNullableDecimal(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    /**
     * Row counts come from a {@code count(*)} delta rather than from the batch return values, because
     * the PostgreSQL driver may answer {@code Statement.SUCCESS_NO_INFO} (-2) per statement, and an
     * upsert's return value cannot distinguish an insert from an update anyway.
     */
    private long countFor(String symbol) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM symbol_events WHERE symbol = ?", Long.class, symbol);
        return count == null ? 0 : count;
    }

    /**
     * Written by hand rather than through the {@code Symbol} entity: {@code events_synced_at} is
     * deliberately unmapped so no unrelated JPA save of a Symbol can flush a null over it.
     */
    private void stampSynced(String symbol) {
        jdbcTemplate.update("UPDATE symbols SET events_synced_at = now() WHERE symbol = ?", symbol);
    }
}
