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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
                total += backfillSymbol(symbol, isin, fromDate, toDate);
                processed++;
            } else {
                boolean fetched = false;

                // Pre-gap: requested start is before our earliest record
                if (fromDate.isBefore(earliest)) {
                    LocalDate preGapEnd = earliest.minusDays(1);
                    if (!fromDate.isAfter(preGapEnd)) {
                        total += backfillSymbol(symbol, isin, fromDate, preGapEnd);
                        fetched = true;
                    }
                }

                // Post-gap: requested end is after our latest record
                if (toDate.isAfter(latest)) {
                    LocalDate postGapStart = latest.plusDays(1);
                    if (!postGapStart.isAfter(toDate)) {
                        total += backfillSymbol(symbol, isin, postGapStart, toDate);
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
     * Incremental backfill for a single symbol.
     * Checks the latest existing record in DB and only fetches from that date onwards.
     * If fromDate is after the latest record, uses the latest record date + 1 as start.
     */
    @Transactional
    public int backfillSymbol(String symbol, String isin, LocalDate fromDate, LocalDate toDate) {
        if (accessToken == null || accessToken.isBlank()) return 0;

        // Incremental: find the latest record we already have
        Optional<StockPriceHistory> latest = historyRepository.findLatest(symbol);
        if (latest.isPresent()) {
            LocalDate latestDate = latest.get().getPriceDate();
            // If we already have data up to or past the requested end, skip entirely
            if (!latestDate.isBefore(toDate)) {
                log.debug("{}: already up to date (latest: {})", symbol, latestDate);
                return 0;
            }
            // Start from the day after the latest record
            LocalDate incrementalFrom = latestDate.plusDays(1);
            if (incrementalFrom.isAfter(fromDate)) {
                fromDate = incrementalFrom;
            }
        }

        // Don't fetch if from > to
        if (fromDate.isAfter(toDate)) {
            log.debug("{}: fromDate {} is after toDate {}, skipping", symbol, fromDate, toDate);
            return 0;
        }

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

            log.info("{}: Upstox response status {}", symbol, response.getStatusCode());
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if (data != null) {
                    List<List<Object>> candles = (List<List<Object>>) data.get("candles");
                    log.info("{}: received {} candles", symbol, candles != null ? candles.size() : 0);
                    if (candles != null) {
                        int saved = 0;
                        for (List<Object> candle : candles) {
                            if (candle.size() < 5) continue;
                            try {
                                String ts = candle.get(0).toString();
                                LocalDate priceDate = LocalDate.parse(ts.substring(0, 10));

                                // Skip if we already have this date
                                if (historyRepository.findBySymbolAndPriceDate(symbol, priceDate).isPresent()) {
                                    continue;
                                }

                                BigDecimal open = toBigDecimal(candle.get(1));
                                BigDecimal high = toBigDecimal(candle.get(2));
                                BigDecimal low = toBigDecimal(candle.get(3));
                                BigDecimal close = toBigDecimal(candle.get(4));
                                Long volume = candle.size() > 5 && candle.get(5) != null
                                        ? ((Number) candle.get(5)).longValue() : null;

                                StockPriceHistory record = StockPriceHistory.builder()
                                        .symbol(symbol)
                                        .priceDate(priceDate)
                                        .open(open)
                                        .high(high)
                                        .low(low)
                                        .close(close)
                                        .volume(volume)
                                        .source("UPSTOX_HIST")
                                        .build();
                                historyRepository.save(record);
                                saved++;
                            } catch (Exception e) {
                                log.warn("Failed to parse candle for {}: {}", symbol, e.getMessage());
                            }
                        }
                        log.debug("{}: fetched {} new records ({} to {})", symbol, saved, fromStr, toStr);
                        return saved;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch historical data for {}: {}", symbol, e.getMessage());
        }
        return 0;
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
