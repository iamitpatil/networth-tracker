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
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


@Service
@RequiredArgsConstructor
@Slf4j
public class AmfiHistoricalService {

    private final StockPriceHistoryRepository historyRepository;
    private final SymbolRepository symbolRepository;
    private final HoldingRepository holdingRepository;
    private final RestTemplate restTemplate;

    private static final String HISTORICAL_URL =
            "https://portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx?tp=1&frmdt=%s&todt=%s";
    private static final DateTimeFormatter AMFI_DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final int MAX_DAYS_PER_REQUEST = 1;

    private final Map<String, Object> backfillStatus = new ConcurrentHashMap<>();
    private final AtomicInteger progressDays = new AtomicInteger(0);
    private final AtomicInteger totalDays = new AtomicInteger(0);

    @Async
    public void triggerBackfill(LocalDate from, LocalDate to) {
        backfillStatus.clear();
        backfillStatus.put("running", true);
        backfillStatus.put("from", from.toString());
        backfillStatus.put("to", to.toString());
        progressDays.set(0);
        totalDays.set((int) java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1);

        try {
            int count = backfillAll(from, to);
            backfillStatus.put("recordsBackfilled", count);
            log.info("Initial MF backfill complete: {} records", count);
        } catch (Exception e) {
            log.error("Initial MF backfill failed: {}", e.getMessage());
            backfillStatus.put("error", e.getMessage());
        } finally {
            backfillStatus.put("running", false);
            backfillStatus.put("completed", true);
        }
    }

    public Map<String, Object> getBackfillStatus() {
        Map<String, Object> status = new LinkedHashMap<>(backfillStatus);
        status.put("progressDays", progressDays.get());
        status.put("totalDays", totalDays.get());
        status.putIfAbsent("running", false);
        status.putIfAbsent("completed", false);
        return status;
    }

    public int backfillAll(LocalDate fromDate, LocalDate toDate) {
        return backfillAll(fromDate, toDate, () -> true);
    }

    /**
     * Backfill with cancellation support and smart date detection.
     * 1. Query DB for earliest + latest MF NAV dates across all symbols
     * 2. Compute actual gaps: pre-gap (before earliest) + post-gap (after latest)
     * 3. Only fetch days we don't already have
     */
    public int backfillAll(LocalDate fromDate, LocalDate toDate, java.util.function.Supplier<Boolean> cancelCheck) {
        Set<String> targetIsins = collectMfIsins();
        if (targetIsins.isEmpty()) {
            log.warn("No MF ISINs found to backfill");
            return 0;
        }

        // Pre-check: find earliest + latest dates for MF symbols (12-char ISINs)
        List<Object[]> dateRanges = historyRepository.findDateRangePerSymbol();
        LocalDate globalEarliest = null;
        LocalDate globalLatest = null;
        int symbolsWithData = 0;

        for (Object[] row : dateRanges) {
            String sym = (String) row[0];
            if (sym != null && sym.length() == 12 && targetIsins.contains(sym)) {
                LocalDate earliest = (LocalDate) row[1];
                LocalDate latest = (LocalDate) row[2];
                symbolsWithData++;
                if (globalEarliest == null || earliest.isBefore(globalEarliest)) globalEarliest = earliest;
                if (globalLatest == null || latest.isAfter(globalLatest)) globalLatest = latest;
            }
        }

        // Build list of date ranges to fetch (pre-gap + post-gap)
        List<LocalDate[]> rangesToFetch = new java.util.ArrayList<>();

        if (globalEarliest == null) {
            // No existing data — fetch full requested range
            rangesToFetch.add(new LocalDate[]{fromDate, toDate});
            log.info("MF pre-check: no existing data, fetching full range {} to {}", fromDate, toDate);
        } else {
            // Pre-gap: requested start is before our earliest record
            if (fromDate.isBefore(globalEarliest)) {
                rangesToFetch.add(new LocalDate[]{fromDate, globalEarliest.minusDays(1)});
                log.info("MF pre-check: pre-gap {} to {} ({} days)",
                        fromDate, globalEarliest.minusDays(1),
                        java.time.temporal.ChronoUnit.DAYS.between(fromDate, globalEarliest));
            }
            // Post-gap: requested end is after our latest record
            if (toDate.isAfter(globalLatest)) {
                rangesToFetch.add(new LocalDate[]{globalLatest.plusDays(1), toDate});
                log.info("MF pre-check: post-gap {} to {} ({} days)",
                        globalLatest.plusDays(1), toDate,
                        java.time.temporal.ChronoUnit.DAYS.between(globalLatest, toDate));
            }
            if (rangesToFetch.isEmpty()) {
                log.info("MF NAV history fully covered ({} symbols, {} to {})", symbolsWithData, globalEarliest, globalLatest);
                return 0;
            }
        }

        int totalSaved = 0;
        int totalDaysToProcess = rangesToFetch.stream()
                .mapToInt(r -> (int) java.time.temporal.ChronoUnit.DAYS.between(r[0], r[1]) + 1)
                .sum();
        totalDays.set(totalDaysToProcess);
        progressDays.set(0);

        for (LocalDate[] range : rangesToFetch) {
            LocalDate chunkStart = range[0];
            LocalDate rangeEnd = range[1];

            while (chunkStart.isBefore(rangeEnd) || chunkStart.isEqual(rangeEnd)) {
                if (!cancelCheck.get()) {
                    log.info("MF backfill cancelled at {}, {} records so far", chunkStart, totalSaved);
                    backfillStatus.put("recordsBackfilled", totalSaved);
                    return totalSaved;
                }
                LocalDate chunkEnd = chunkStart.plusDays(MAX_DAYS_PER_REQUEST - 1);
                if (chunkEnd.isAfter(rangeEnd)) chunkEnd = rangeEnd;
                int saved = backfillChunk(chunkStart, chunkEnd, targetIsins);
                totalSaved += saved;
                progressDays.incrementAndGet();
                chunkStart = chunkEnd.plusDays(1);
            }
        }

        log.info("Backfilled {} MF NAV history records ({} days across {} ranges)",
                totalSaved, totalDaysToProcess, rangesToFetch.size());
        backfillStatus.put("recordsBackfilled", totalSaved);
        return totalSaved;
    }

    @Transactional
    public int backfillChunk(LocalDate from, LocalDate to, Set<String> targetIsins) {
        String fromStr = from.format(AMFI_DATE_FMT);
        String toStr = to.format(AMFI_DATE_FMT);
        String url = String.format(HISTORICAL_URL, fromStr, toStr);

        try {
            String response = restTemplate.getForObject(url, String.class);
            if (response == null || response.isBlank()) return 0;

            String[] lines = response.split("\n");
            int saved = 0;

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("Scheme Code") || !line.contains(";")) continue;

                String[] cols = line.split(";");
                if (cols.length < 8) continue;

                String isinGrowth = cols.length > 2 ? cols[2].trim() : "";
                String isinReinv = cols.length > 3 ? cols[3].trim() : "";
                String navStr = cols.length > 4 ? cols[4].trim() : "";
                String dateStr = cols.length > 7 ? cols[7].trim() : "";

                String matchedIsin = null;
                if (isinGrowth.length() == 12 && !isinGrowth.equals("-") && targetIsins.contains(isinGrowth)) {
                    matchedIsin = isinGrowth;
                } else if (isinReinv.length() == 12 && !isinReinv.equals("-") && targetIsins.contains(isinReinv)) {
                    matchedIsin = isinReinv;
                }

                if (matchedIsin == null) continue;
                if (navStr.isEmpty() || dateStr.isEmpty()) continue;

                try {
                    BigDecimal nav = new BigDecimal(navStr);
                    LocalDate priceDate = LocalDate.parse(dateStr, AMFI_DATE_FMT);

                    if (historyRepository.findBySymbolAndPriceDate(matchedIsin, priceDate).isPresent()) continue;

                    StockPriceHistory record = StockPriceHistory.builder()
                            .symbol(matchedIsin)
                            .priceDate(priceDate)
                            .close(nav)
                            .source("AMFI")
                            .build();
                    historyRepository.save(record);
                    saved++;
                } catch (Exception e) {
                    log.warn("Failed to parse AMFI entry: {} {}", matchedIsin, e.getMessage());
                }
            }

            return saved;
        } catch (Exception e) {
            log.error("Failed to fetch AMFI historical data ({} to {}): {}", fromStr, toStr, e.getMessage());
            return 0;
        }
    }

    private Set<String> collectMfIsins() {
        Set<String> isins = new HashSet<>();

        holdingRepository.findAll().stream()
                .filter(h -> h.getAssetType() == AssetType.MUTUAL_FUND)
                .map(Holding::getIsin)
                .filter(s -> s != null && !s.isBlank())
                .forEach(isins::add);

        symbolRepository.findByCategory("MUTUAL_FUND")
                .stream()
                .map(Symbol::getSymbol)
                .filter(s -> s != null && s.length() == 12)
                .forEach(isins::add);

        if (!isins.isEmpty()) {
            log.info("Collected {} MF ISINs for backfill ({} from holdings, {} from symbols)",
                    isins.size(),
                    isins.stream().filter(i -> holdingRepository.findAll().stream()
                            .anyMatch(h -> i.equals(h.getIsin()))).count(),
                    isins.size());
        }

        return isins;
    }

    @Scheduled(cron = "0 30 3 * * ?")
    public void scheduledDailyBackfill() {
        log.info("Running daily AMFI NAV history backfill...");
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(5);
            int count = backfillAll(from, to);
            log.info("Daily AMFI backfill complete: {} records added", count);
        } catch (Exception e) {
            log.error("Daily AMFI backfill failed: {}", e.getMessage());
        }
    }
}
