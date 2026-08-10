package com.networth.service.market;

import com.networth.model.entity.Holding;
import com.networth.model.entity.NpsAccount;
import com.networth.model.entity.StockPriceHistory;
import com.networth.model.entity.Symbol;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.NpsAccountRepository;
import com.networth.repository.StockPriceHistoryRepository;
import com.networth.repository.SymbolRepository;
import com.networth.service.market.provider.ProviderRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Day-wise NPS NAV history, persisted so a pension fund can be valued at a past date.
 *
 * <p>Without this a pension fund is the one asset whose entire point — compounding — cannot be shown.
 * {@code NpsNavService} already fetches history from {@code /api/historical/{code}}, but only ever to
 * answer a request; nothing wrote it down, so {@code InvestmentOverTimeService} had no NAV to look up
 * and valued NPS at {@code units × last transaction price}: a flat line at cost.
 *
 * <p>Rows land in {@code stock_price_history} rather than a new table, keyed by scheme code with
 * {@code close = nav} and {@code source = NPSNAV}. That is the same table AMFI NAVs already share with
 * Upstox candles, and it is keyed by whatever {@code HoldingService.getEffectiveSymbolForPricing}
 * returns for the holding — for NPS, the scheme code. {@code open}/{@code high}/{@code low}/
 * {@code volume} stay null, exactly as they do for an AMFI row: a NAV has no intraday shape.
 *
 * <p>Written through {@link JdbcTemplate#batchUpdate} with {@code ON CONFLICT DO NOTHING} rather than
 * {@code save()} per row. Two reasons, both load-bearing at this size — the full load is ~1.28M rows
 * across 282 schemes:
 *
 * <ul>
 *   <li>no {@code hibernate.jdbc.batch_size} is configured, so {@code saveAll} would issue one round
 *       trip per row, and {@code AmfiHistoricalService} additionally does a {@code SELECT} per row to
 *       decide whether to skip it — three million statements for a load that is a few hundred here
 *   <li>the conflict clause makes the whole thing incremental for free: a re-run re-fetches the JSON
 *       but writes only the days that are new, so an interrupted backfill resumes by being run again
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NpsHistoricalService {

    private final NpsNavService npsNavService;
    private final ProviderRateLimiter rateLimiter;
    private final StockPriceHistoryRepository historyRepository;
    private final SymbolRepository symbolRepository;
    private final HoldingRepository holdingRepository;
    private final NpsAccountRepository npsAccountRepository;
    private final JdbcTemplate jdbcTemplate;

    /** npsnav.in's date format, the same one {@code NpsNavService} already parses NAV dates with. */
    private static final DateTimeFormatter NAV_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    static final String SOURCE = "NPSNAV";

    /**
     * Earlier than any NPS NAV: the scheme series begins 2008-04-01. Used as the lower bound of a full
     * historical load, so "everything the API has" needs no null-means-unbounded special case.
     */
    static final LocalDate FULL_HISTORY_START = LocalDate.of(2000, 1, 1);

    /** Rows per JDBC batch, matching {@code SymbolService}. */
    private static final int BATCH_SIZE = 1000;

    /**
     * How long a backfill will wait for a rate-limit slot before skipping a scheme.
     *
     * <p>Long, unlike the live read path's non-blocking {@code tryAcquire}: there is no second provider
     * for an NPS NAV, so waiting is the only alternative to leaving a permanent hole in one scheme's
     * history. The npsnav budget is 1/s, so the wait is normally about a second.
     */
    private static final Duration SLOT_WAIT = Duration.ofSeconds(10);

    private static final String INSERT_SQL = """
            INSERT INTO stock_price_history (symbol, price_date, close, source)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (symbol, price_date) DO NOTHING
            """;

    /** Progress during a long backfill, shaped like the callbacks {@code BackfillJobService} already passes. */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int processedSchemes, int totalSchemes, int records);
    }

    // ── one scheme ────────────────────────────────────────────────────────────

    /**
     * Persist one scheme's NAVs for the days in {@code [from, to]}.
     *
     * <p>The API takes no date range — one call returns a scheme's entire history — so the window is a
     * filter on what gets written, not on what gets fetched. It still matters: a daily top-up writes
     * the handful of new days rather than handing the database 4,500 rows to reject.
     *
     * @return how many rows were actually inserted, counted as a delta rather than taken from the
     *         batch return value, because PostgreSQL's driver may answer {@code SUCCESS_NO_INFO}
     */
    @Transactional
    public int backfillScheme(String schemeCode, LocalDate from, LocalDate to) {
        if (schemeCode == null || schemeCode.isBlank() || from.isAfter(to)) {
            return 0;
        }

        if (!rateLimiter.acquire("npsnav", SLOT_WAIT)) {
            log.info("{}: skipped, the npsnav budget is exhausted; the next run will pick up the gap",
                    schemeCode);
            return 0;
        }

        List<Map<String, Object>> history = npsNavService.getHistoricalNav(schemeCode);
        if (history.isEmpty()) {
            log.warn("No NPS history returned for {}", schemeCode);
            return 0;
        }

        List<Object[]> rows = new ArrayList<>();
        int unparseable = 0;
        for (Map<String, Object> entry : history) {
            LocalDate date = parseDate(entry.get("date"));
            BigDecimal nav = parseNav(entry.get("nav"));
            if (date == null || nav == null) {
                unparseable++;
                continue;
            }
            if (date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            rows.add(new Object[]{schemeCode, date, nav});
        }
        if (unparseable > 0) {
            log.warn("{}: {} of {} NPS history entries could not be parsed", schemeCode, unparseable, history.size());
        }
        if (rows.isEmpty()) {
            return 0;
        }

        long before = countFor(schemeCode);
        insert(rows);
        int inserted = (int) (countFor(schemeCode) - before);
        log.debug("{}: {} NAV days in range, {} new", schemeCode, rows.size(), inserted);
        return inserted;
    }

    /** Everything the API has for one scheme. */
    public int backfillScheme(String schemeCode) {
        return backfillScheme(schemeCode, FULL_HISTORY_START, LocalDate.now(MarketCalendar.ZONE));
    }

    // ── every scheme ──────────────────────────────────────────────────────────

    public int backfillAll() {
        return backfillAll(() -> true, null);
    }

    /**
     * The full historical load: every listed scheme, back to whenever its NAV series starts.
     *
     * <p>Runs in the background, never at boot. Only the 282-row <em>scheme list</em> is needed before
     * the app serves traffic — that is what ticker validation checks against, and
     * {@code SymbolBootstrapService} loads it. A million NAV rows are needed only by a chart.
     *
     * <p>Cancellation is checked per scheme rather than per row: one scheme is one HTTP call plus a
     * handful of batched statements, so the longest a cancel waits is that.
     */
    public int backfillAll(Supplier<Boolean> cancelCheck, ProgressCallback progress) {
        List<String> schemes = listedSchemes();
        if (schemes.isEmpty()) {
            log.warn("No NPS schemes to backfill — the reference list is empty");
            return 0;
        }

        LocalDate today = LocalDate.now(MarketCalendar.ZONE);
        int records = 0;
        int processed = 0;

        for (String schemeCode : schemes) {
            if (!cancelCheck.get()) {
                log.info("NPS history backfill cancelled at {}/{} schemes, {} records",
                        processed, schemes.size(), records);
                return records;
            }
            try {
                records += backfillScheme(schemeCode, FULL_HISTORY_START, today);
            } catch (Exception e) {
                // One dead scheme must not cost the other 281.
                log.warn("NPS history backfill failed for {}: {}", schemeCode, e.getMessage());
            }
            processed++;
            if (progress != null) {
                progress.onProgress(processed, schemes.size(), records);
            }
            if (processed % 25 == 0) {
                log.info("NPS history backfill progress: {}/{} schemes, {} records",
                        processed, schemes.size(), records);
            }
        }

        log.info("Backfilled {} NPS NAV history records across {} schemes", records, processed);
        return records;
    }

    /**
     * Top up the history of the schemes somebody actually holds, from the last day we have.
     *
     * <p>This is the incremental half, and the one that runs on a schedule and at startup. The full
     * 282-scheme load is a one-time job; a household holds a handful of schemes, and fetching the
     * other 275 every night would spend the budget on history nobody will open.
     */
    public int backfillHeldSchemes() {
        Set<String> schemes = heldSchemeCodes();
        if (schemes.isEmpty()) {
            log.info("No NPS holdings need NAV history");
            return 0;
        }

        LocalDate today = LocalDate.now(MarketCalendar.ZONE);
        int records = 0;
        for (String schemeCode : schemes) {
            Optional<StockPriceHistory> latest = historyRepository.findLatest(schemeCode);
            LocalDate from;
            if (latest.isPresent()) {
                LocalDate latestDate = latest.get().getPriceDate();
                if (!latestDate.isBefore(today)) {
                    log.debug("{}: NAV history up to date (latest {})", schemeCode, latestDate);
                    continue;
                }
                from = latestDate.plusDays(1);
            } else {
                // No history at all: take the lot. One call returns it anyway, so a 365-day cap would
                // buy nothing and would leave the chart short for a fund held longer than that.
                from = FULL_HISTORY_START;
            }
            try {
                records += backfillScheme(schemeCode, from, today);
            } catch (Exception e) {
                log.warn("NPS history top-up failed for {}: {}", schemeCode, e.getMessage());
            }
        }

        log.info("NPS NAV history top-up: {} records across {} held schemes", records, schemes.size());
        return records;
    }

    /**
     * Nightly top-up, after the NAVs for the day are published.
     *
     * <p>21:15 IST, deliberately a quarter past: {@code NpsNavService} refreshes spot NAVs at 21:00 and
     * the equity and AMFI history jobs run at 03:00 and 03:30, so this shares a window with nothing.
     */
    @Scheduled(cron = "0 15 21 * * ?", zone = "Asia/Kolkata")
    public void scheduledDailyTopUp() {
        log.info("Running daily NPS NAV history top-up...");
        try {
            int count = backfillHeldSchemes();
            log.info("Daily NPS NAV history top-up complete: {} records added", count);
        } catch (Exception e) {
            log.error("Daily NPS NAV history top-up failed: {}", e.getMessage());
        }
    }

    // ── work lists ────────────────────────────────────────────────────────────

    /**
     * Every scheme we know of, from the reference table the pre-start loader fills.
     *
     * <p>Read from {@code symbols} rather than from the API so a full backfill does not depend on a
     * second network call succeeding, and so the codes here are exactly the ones ticker validation
     * accepts. Falls back to the API only if the table is empty, which means the loader failed.
     */
    private List<String> listedSchemes() {
        List<String> codes = symbolRepository.findByCategory("NPS").stream()
                .map(Symbol::getSymbol)
                .filter(s -> s != null && !s.isBlank())
                .toList();
        if (!codes.isEmpty()) {
            return codes;
        }
        log.warn("No NPS rows in the symbols table; falling back to the npsnav scheme list");
        return npsNavService.getSchemes().stream()
                .map(s -> s.get("schemeCode"))
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    /**
     * The scheme codes somebody holds, from both places NPS is recorded.
     *
     * <p>An NPS position can arrive as a {@code Holding} of type NPS — whose symbol validation now
     * forces to be a listed scheme code — or as an {@code NpsAccount} with a {@code scheme_code}. Both
     * are priced from the same NAV series, so both belong on this list.
     */
    private Set<String> heldSchemeCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (Holding h : holdingRepository.findAllActive()) {
            if (h.getAssetType() == AssetType.NPS && h.getSymbol() != null && !h.getSymbol().isBlank()) {
                codes.add(h.getSymbol().trim());
            }
        }
        for (NpsAccount account : npsAccountRepository.findAll()) {
            if (account.getSchemeCode() != null && !account.getSchemeCode().isBlank()) {
                codes.add(account.getSchemeCode().trim());
            }
        }
        return codes;
    }

    // ── persistence ───────────────────────────────────────────────────────────

    private void insert(List<Object[]> rows) {
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<Object[]> chunk = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Object[] row = chunk.get(i);
                    ps.setString(1, (String) row[0]);
                    ps.setDate(2, Date.valueOf((LocalDate) row[1]));
                    ps.setBigDecimal(3, (BigDecimal) row[2]);
                    ps.setString(4, SOURCE);
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
    }

    private long countFor(String schemeCode) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_price_history WHERE symbol = ?", Long.class, schemeCode);
        return count == null ? 0 : count;
    }

    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.toString().trim(), NAV_DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    /** A NAV arrives as a JSON number, but a string is cheap to tolerate and some entries are one. */
    private BigDecimal parseNav(Object value) {
        if (value == null) {
            return null;
        }
        try {
            BigDecimal nav = value instanceof Number n
                    ? BigDecimal.valueOf(n.doubleValue())
                    : new BigDecimal(value.toString().trim());
            // A zero or negative NAV is not a price, it is a hole in the feed. Storing it would show
            // the fund's value collapsing to nothing on that day.
            return nav.signum() > 0 ? nav : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
