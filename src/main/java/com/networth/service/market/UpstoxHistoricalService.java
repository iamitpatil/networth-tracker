package com.networth.service.market;

import com.networth.model.entity.Holding;
import com.networth.model.entity.StockPriceHistory;
import com.networth.model.entity.Symbol;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.StockPriceHistoryRepository;
import com.networth.repository.SymbolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpstoxHistoricalService {

    private final RestTemplate restTemplate;
    private final com.networth.service.market.provider.ProviderRateLimiter rateLimiter;
    private final HoldingRepository holdingRepository;
    private final StockPriceHistoryRepository historyRepository;
    private final SymbolRepository symbolRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * The longest window Upstox will serve in one historical-candle request.
     *
     * <p>Measured, not guessed: a ten-year request returns 2,477 daily candles, and fifteen years is
     * rejected outright with {@code UDAPI1148 Invalid date range}. A load from inception is therefore
     * three requests per symbol rather than one, and asking for the whole span in one call would fail
     * for every symbol.
     */
    static final int MAX_WINDOW_YEARS = 10;

    /** Rows per JDBC batch, matching the other loaders. */
    private static final int BATCH_SIZE = 1000;

    /**
     * One statement instead of a SELECT and an INSERT per candle.
     *
     * <p>This method used to call {@code findBySymbolAndPriceDate} and then {@code save} for every
     * candle — two round trips a row, with no {@code hibernate.jdbc.batch_size} configured to amortise
     * them. At inception depth that is roughly 6,600 candles a symbol, so a two-dozen-symbol load meant
     * over 300,000 round trips. {@code uq_stock_price_date} already exists, so the duplicate check is
     * work the database does better than a pre-query.
     */
    private static final String INSERT_SQL = """
            INSERT INTO stock_price_history (symbol, price_date, open, high, low, close, volume, source)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (symbol, price_date) DO NOTHING
            """;

    @Value("${market.data.upstox.access-token:}")
    private String accessToken;

    @Value("${market.data.upstox.base-url:https://api.upstox.com/v3}")
    private String baseUrl;

    /**
     * Backfill price history for every equity or ETF somebody holds.
     */
    @Transactional
    public int backfillAll(LocalDate fromDate, LocalDate toDate) {
        return backfillAll(fromDate, toDate, () -> true);
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int processed, int total, int records, int skipped);
    }

    @Transactional
    public int backfillAll(LocalDate fromDate, LocalDate toDate, java.util.function.Supplier<Boolean> cancelCheck) {
        return backfillAll(fromDate, toDate, cancelCheck, null);
    }

    /**
     * Backfill with cancellation + progress reporting.
     *
     * <p>The work list is the instruments somebody actually holds. It used to be every EQUITY row in
     * the symbols table — a couple of thousand instruments, of which a household holds perhaps thirty
     * — and then the holdings again on top, so a held symbol was fetched twice per run. At the rate
     * limiter's pace that is hours of requests building history nobody will look at, and the 03:00 job
     * spent its Upstox budget on unheld symbols before reaching the ones on somebody's screen. History
     * for an unheld symbol is still fetched on demand, by {@link #backfillSymbol}, when its chart is
     * opened.
     */
    @Transactional
    public int backfillAll(LocalDate fromDate, LocalDate toDate, java.util.function.Supplier<Boolean> cancelCheck, ProgressCallback progress) {
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("No Upstox analytics token configured, skipping equity backfill");
            return 0;
        }

        // Bulk pre-check: get earliest + latest date per symbol in one query
        Map<String, LocalDate> earliestDates = new java.util.HashMap<>();
        Map<String, LocalDate> latestDates = new java.util.HashMap<>();
        for (Object[] row : historyRepository.findDateRangePerSymbol()) {
            String sym = (String) row[0];
            earliestDates.put(sym, (LocalDate) row[1]);
            latestDates.put(sym, (LocalDate) row[2]);
        }
        log.info("Equity pre-check: {} symbols already have price history in DB", latestDates.size());

        Collection<Holding> targets = heldEquities();
        int total = 0;
        int processed = 0;
        int skippedNoIsin = 0;
        int skippedFullyCovered = 0;

        for (Holding target : targets) {
            if (!cancelCheck.get()) {
                log.info("Equity backfill cancelled at {}/{} symbols, {} records", processed, targets.size(), total);
                return total;
            }
            // Resolved here rather than filtered on beforehand: a holding imported from a broker often
            // arrives without an ISIN, and this writes the one it finds back to the row, so the price
            // sweep and every later backfill can use it.
            String isin = resolveAndPersistIsin(target);
            if (isin == null || isin.isBlank()) {
                skippedNoIsin++;
                continue;
            }

            String symbol = target.getSymbol();
            LocalDate earliest = earliestDates.get(symbol);
            LocalDate latest = latestDates.get(symbol);

            if (earliest == null) {
                // No data at all — fetch full range
                total += fetchAndStore(symbol, isin, fromDate, toDate);
                processed++;
            } else {
                boolean fetched = false;

                // Pre-gap: requested start is before our earliest record.
                //
                // fetchAndStore, not backfillSymbol. The latter clamps the window to "everything after
                // the newest row we hold", which for a backward fill is always the empty set, so this
                // branch silently did nothing for any symbol that had even one row. That is why history
                // stayed pinned to whichever window first created it.
                if (fromDate.isBefore(earliest)) {
                    LocalDate preGapEnd = earliest.minusDays(1);
                    if (!fromDate.isAfter(preGapEnd)) {
                        total += fetchAndStore(symbol, isin, fromDate, preGapEnd);
                        fetched = true;
                    }
                }

                // Post-gap: requested end is after our latest record
                if (toDate.isAfter(latest)) {
                    LocalDate postGapStart = latest.plusDays(1);
                    if (!postGapStart.isAfter(toDate)) {
                        total += fetchAndStore(symbol, isin, postGapStart, toDate);
                        fetched = true;
                    }
                }

                if (!fetched) {
                    skippedFullyCovered++;
                }
                processed++;
            }

            if (progress != null) {
                progress.onProgress(processed, targets.size(), total, skippedFullyCovered);
            }
            if (processed % 100 == 0) {
                log.info("Equity backfill progress: {}/{} processed ({} skipped), {} records",
                        processed, targets.size(), skippedFullyCovered, total);
            }
        }

        log.info("Backfilled {} equity price records ({} of {} held instruments processed, {} fully covered, {} no ISIN)",
                total, processed, targets.size(), skippedFullyCovered, skippedNoIsin);
        return total;
    }

    /**
     * One live holding per equity or ETF symbol, preferring a row that already carries an ISIN.
     *
     * <p>One row per <em>symbol</em>, not per holding: the same stock in two demat accounts and again
     * under another family member is one price history. Soft-deleted rows are left out — a sold-out
     * position needs no further candles.
     */
    private Collection<Holding> heldEquities() {
        Map<String, Holding> bySymbol = new java.util.LinkedHashMap<>();
        for (Holding h : holdingRepository.findAllActive()) {
            if (h.getAssetType() != AssetType.EQUITY && h.getAssetType() != AssetType.ETF) {
                continue;
            }
            if (h.getSymbol() == null || h.getSymbol().isBlank()) {
                continue;
            }
            Holding existing = bySymbol.get(h.getSymbol());
            boolean existingHasIsin = existing != null
                    && existing.getIsin() != null && !existing.getIsin().isBlank();
            if (existing == null || !existingHasIsin) {
                bySymbol.put(h.getSymbol(), h);
            }
        }
        return bySymbol.values();
    }

    /**
     * Incremental backfill for a single symbol, clamped to what we do not already have.
     *
     * <p>For callers holding no gap information of their own — the scheduled daily job and
     * {@code StartupBackfillService} — where "everything since the newest row" is exactly right.
     *
     * <p>{@link #fetchAndStore} is the unclamped form, and {@link #backfillAll} uses that instead. The
     * clamp here can only ever move the window <em>forward</em>, so it is wrong for filling a hole
     * before the newest row: see the note on {@code fetchAndStore}.
     */
    @Transactional
    public int backfillSymbol(String symbol, String isin, LocalDate fromDate, LocalDate toDate) {
        if (accessToken == null || accessToken.isBlank()) return 0;

        Optional<StockPriceHistory> latest = historyRepository.findLatest(symbol);
        if (latest.isPresent()) {
            LocalDate latestDate = latest.get().getPriceDate();
            // Already have data up to or past the requested end
            if (!latestDate.isBefore(toDate)) {
                log.debug("{}: already up to date (latest: {})", symbol, latestDate);
                return 0;
            }
            LocalDate incrementalFrom = latestDate.plusDays(1);
            if (incrementalFrom.isAfter(fromDate)) {
                fromDate = incrementalFrom;
            }
        }
        return fetchAndStore(symbol, isin, fromDate, toDate);
    }

    /**
     * Fetch exactly the window asked for and store it, with no clamping of any kind.
     *
     * <p>The absence of the clamp is the point. {@link #backfillAll} computes a pre-gap window — from the
     * requested start up to the day before the symbol's earliest stored row — and used to hand it to
     * {@link #backfillSymbol}, whose first act is {@code if (!latestDate.isBefore(toDate)) return 0}. For
     * a pre-gap fetch {@code toDate} is by construction <em>before</em> the newest row, so that test was
     * always true and the branch always returned 0. Backward extension was dead code: a symbol's history
     * could only ever grow forwards from whatever window first created it, which is why twelve symbols
     * sat at exactly one rolling year and no amount of re-running the backfill moved them.
     *
     * <p>Long windows are split, because Upstox rejects anything over ten years outright.
     *
     * @return how many rows were actually inserted
     */
    int fetchAndStore(String symbol, String isin, LocalDate fromDate, LocalDate toDate) {
        if (accessToken == null || accessToken.isBlank()) return 0;
        if (fromDate.isAfter(toDate)) {
            log.debug("{}: fromDate {} is after toDate {}, skipping", symbol, fromDate, toDate);
            return 0;
        }

        int total = 0;
        LocalDate windowStart = fromDate;
        while (!windowStart.isAfter(toDate)) {
            LocalDate windowEnd = windowStart.plusYears(MAX_WINDOW_YEARS).minusDays(1);
            if (windowEnd.isAfter(toDate)) {
                windowEnd = toDate;
            }
            total += fetchWindow(symbol, isin, windowStart, windowEnd);
            windowStart = windowEnd.plusDays(1);
        }
        return total;
    }

    /** One request, one window no longer than {@link #MAX_WINDOW_YEARS}. */
    private int fetchWindow(String symbol, String isin, LocalDate fromDate, LocalDate toDate) {
        try {
            String instrumentKey = "NSE_EQ|" + isin;
            String toStr = toDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String fromStr = fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String url = baseUrl + "/historical-candle/" + instrumentKey + "/days/1/" + toStr + "/" + fromStr;
            log.info("Fetching historical data for {} (ISIN: {}): {} to {}", symbol, isin, fromStr, toStr);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("Authorization", "Bearer " + accessToken);

            // Waits for a slot rather than skipping: this is a background backfill with no
            // alternative provider, so pausing briefly is better than leaving a gap in the symbol's
            // history that nothing will come back for. A separate bucket from the quote API,
            // because Upstox counts its limits per API and this is where a backfill spends them.
            if (!rateLimiter.acquire("upstox-historical")) {
                log.info("{}: skipped, the Upstox historical-candle budget is exhausted; the next "
                        + "scheduled backfill will pick up the gap", symbol);
                return 0;
            }

            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("{}: Upstox returned {}", symbol, response.getStatusCode());
                return 0;
            }
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            if (data == null) return 0;

            List<List<Object>> candles = (List<List<Object>>) data.get("candles");
            if (candles == null || candles.isEmpty()) {
                // Normal, not an error: a window that starts before the stock listed comes back empty
                // rather than failing, which is what lets a single inception date serve every symbol.
                log.debug("{}: no candles for {} to {}", symbol, fromStr, toStr);
                return 0;
            }

            List<Object[]> rows = new ArrayList<>(candles.size());
            for (List<Object> candle : candles) {
                if (candle.size() < 5) continue;
                try {
                    String ts = candle.get(0).toString();
                    rows.add(new Object[]{
                            symbol,
                            LocalDate.parse(ts.substring(0, 10)),
                            toBigDecimal(candle.get(1)),
                            toBigDecimal(candle.get(2)),
                            toBigDecimal(candle.get(3)),
                            toBigDecimal(candle.get(4)),
                            candle.size() > 5 && candle.get(5) != null
                                    ? ((Number) candle.get(5)).longValue() : null,
                    });
                } catch (Exception e) {
                    log.warn("Failed to parse candle for {}: {}", symbol, e.getMessage());
                }
            }

            int inserted = insert(rows);
            log.info("{}: {} candles received, {} new rows stored ({} to {})",
                    symbol, candles.size(), inserted, fromStr, toStr);
            return inserted;
        } catch (Exception e) {
            log.error("Failed to fetch historical data for {}: {}", symbol, e.getMessage());
            return 0;
        }
    }

    /**
     * Batched insert, conflict-skipped.
     *
     * <p>Inserted counts come from a {@code count(*)} delta rather than the batch return values, because
     * the PostgreSQL driver may answer {@code Statement.SUCCESS_NO_INFO} (-2) per statement.
     */
    private int insert(List<Object[]> rows) {
        if (rows.isEmpty()) return 0;
        String symbol = (String) rows.get(0)[0];
        long before = countFor(symbol);
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<Object[]> chunk = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Object[] row = chunk.get(i);
                    ps.setString(1, (String) row[0]);
                    ps.setDate(2, Date.valueOf((LocalDate) row[1]));
                    setNullableDecimal(ps, 3, (BigDecimal) row[2]);
                    setNullableDecimal(ps, 4, (BigDecimal) row[3]);
                    setNullableDecimal(ps, 5, (BigDecimal) row[4]);
                    setNullableDecimal(ps, 6, (BigDecimal) row[5]);
                    if (row[6] == null) {
                        ps.setNull(7, Types.BIGINT);
                    } else {
                        ps.setLong(7, (Long) row[6]);
                    }
                    ps.setString(8, "UPSTOX_HIST");
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
        return (int) (countFor(symbol) - before);
    }

    private void setNullableDecimal(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    private long countFor(String symbol) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_price_history WHERE symbol = ?", Long.class, symbol);
        return count == null ? 0 : count;
    }

    public List<StockPriceHistory> getPriceHistory(String symbol, LocalDate fromDate, LocalDate toDate) {
        return historyRepository.findBySymbolAndPriceDateBetweenOrderByPriceDate(symbol, fromDate, toDate);
    }

    public Optional<BigDecimal> getPreviousClose(String symbol) {
        return historyRepository.findLatest(symbol)
                .map(StockPriceHistory::getClose);
    }

    /**
     * Daily backfill at 3 AM. Only fetches the gap since the last record per symbol.
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Kolkata")
    public void scheduledDailyBackfill() {
        if (accessToken == null || accessToken.isBlank()) return;
        log.info("Running daily price history backfill...");
        try {
            LocalDate to = LocalDate.now(MarketCalendar.ZONE);
            LocalDate from = to.minusDays(7);
            int count = backfillAll(from, to);
            log.info("Daily backfill complete: {} records added", count);
        } catch (Exception e) {
            log.error("Daily backfill failed: {}", e.getMessage());
        }
    }

    @Transactional
    public int backfillFromDate(LocalDate fromDate) {
        return backfillAll(fromDate, LocalDate.now(MarketCalendar.ZONE));
    }

    /**
     * Resolve ISIN for a holding. If the holding doesn't have one,
     * look it up from the symbols table and persist it on the holding.
     */
    private String resolveAndPersistIsin(Holding holding) {
        if (holding.getIsin() != null && !holding.getIsin().isBlank()) {
            return holding.getIsin();
        }

        // Try symbols table
        Optional<Symbol> symbol = symbolRepository.findById(holding.getSymbol());
        if (symbol.isPresent() && symbol.get().getIsin() != null && !symbol.get().getIsin().isBlank()) {
            String isin = symbol.get().getIsin();
            holding.setIsin(isin);
            holdingRepository.save(holding);
            log.info("Resolved ISIN for {}: {}", holding.getSymbol(), isin);
            return isin;
        }

        // Try without .NS suffix
        String clean = holding.getSymbol().replace(".NS", "").replace(".BSE", "");
        Optional<Symbol> cleanSymbol = symbolRepository.findById(clean + ".NS");
        if (cleanSymbol.isPresent() && cleanSymbol.get().getIsin() != null && !cleanSymbol.get().getIsin().isBlank()) {
            String isin = cleanSymbol.get().getIsin();
            holding.setIsin(isin);
            holdingRepository.save(holding);
            log.info("Resolved ISIN for {}: {}", holding.getSymbol(), isin);
            return isin;
        }

        log.warn("No ISIN found for {}", holding.getSymbol());
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
