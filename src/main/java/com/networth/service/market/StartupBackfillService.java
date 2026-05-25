package com.networth.service.market;

import com.networth.model.entity.Holding;
import com.networth.model.entity.StockPriceHistory;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.StockPriceHistoryRepository;
import com.networth.repository.UserRepository;
import com.networth.service.NetWorthHistoryService;
import com.networth.service.portfolio.HoldingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * On application startup, detects gaps in historical data and backfills them.
 * Critical when app has been down - prevents broken charts and missing data points.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StartupBackfillService {

    private static final int MAX_INITIAL_BACKFILL_DAYS = 365;
    private static final int MIN_GAP_DAYS_TO_BACKFILL = 1;

    private final HoldingRepository holdingRepository;
    private final HoldingService holdingService;
    private final StockPriceHistoryRepository historyRepository;
    private final UpstoxHistoricalService upstoxHistoricalService;
    private final AmfiHistoricalService amfiHistoricalService;
    private final NetWorthHistoryService netWorthHistoryService;
    private final UserRepository userRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        log.info("=== Starting backfill check on application startup ===");
        try {
            Thread.sleep(5000); // Let other beans init

            // Ensure all holdings have ISINs before backfilling prices
            holdingService.backfillMissingIsins();

            backfillEquityHistory();
            backfillMutualFundHistory();
            backfillNetWorthHistory();

            log.info("=== Startup backfill complete ===");
        } catch (Exception e) {
            log.error("Startup backfill failed: {}", e.getMessage(), e);
        }
    }

    private void backfillEquityHistory() {
        try {
            List<Holding> equityHoldings = holdingRepository.findAll().stream()
                    .filter(h -> h.getAssetType() == AssetType.EQUITY || h.getAssetType() == AssetType.ETF)
                    .filter(h -> h.getIsin() != null && !h.getIsin().isBlank())
                    .toList();

            if (equityHoldings.isEmpty()) {
                log.info("No equity holdings need backfill");
                return;
            }

            Map<String, String> symbolToIsin = new HashMap<>();
            for (Holding h : equityHoldings) {
                symbolToIsin.put(h.getSymbol(), h.getIsin());
            }

            int totalBackfilled = 0;
            int symbolsBackfilled = 0;
            LocalDate today = LocalDate.now();

            for (Map.Entry<String, String> entry : symbolToIsin.entrySet()) {
                String symbol = entry.getKey();
                String isin = entry.getValue();

                Optional<StockPriceHistory> latest = historyRepository.findLatest(symbol);

                LocalDate fromDate;
                if (latest.isPresent()) {
                    LocalDate latestDate = latest.get().getPriceDate();
                    long daysSince = ChronoUnit.DAYS.between(latestDate, today);

                    if (daysSince < MIN_GAP_DAYS_TO_BACKFILL) {
                        log.debug("{}: up to date (latest: {})", symbol, latestDate);
                        continue;
                    }

                    fromDate = latestDate.plusDays(1);
                    log.info("{}: gap of {} days detected, backfilling from {}", symbol, daysSince, fromDate);
                } else {
                    fromDate = today.minusDays(MAX_INITIAL_BACKFILL_DAYS);
                    log.info("{}: no history found, doing initial backfill from {}", symbol, fromDate);
                }

                try {
                    int count = upstoxHistoricalService.backfillSymbol(symbol, isin, fromDate, today);
                    if (count > 0) {
                        totalBackfilled += count;
                        symbolsBackfilled++;
                        log.info("{}: backfilled {} price records", symbol, count);
                    }
                    Thread.sleep(200);
                } catch (Exception e) {
                    log.warn("Failed to backfill {}: {}", symbol, e.getMessage());
                }
            }

            log.info("Equity backfill complete: {} records across {} symbols",
                    totalBackfilled, symbolsBackfilled);
        } catch (Exception e) {
            log.error("Equity backfill error: {}", e.getMessage(), e);
        }
    }

    private void backfillMutualFundHistory() {
        try {
            List<Holding> mfHoldings = holdingRepository.findAll().stream()
                    .filter(h -> h.getAssetType() == AssetType.MUTUAL_FUND)
                    .filter(h -> h.getSymbol() != null && !h.getSymbol().isBlank())
                    .toList();

            if (mfHoldings.isEmpty()) {
                log.info("No mutual fund holdings need backfill");
                return;
            }

            LocalDate today = LocalDate.now();
            LocalDate oldestGap = today;
            boolean anyMissing = false;

            for (Holding h : mfHoldings) {
                String symbol = holdingService.getEffectiveSymbolForPricing(h);
                Optional<StockPriceHistory> latest = historyRepository.findLatest(symbol);
                if (latest.isPresent()) {
                    LocalDate latestDate = latest.get().getPriceDate();
                    if (latestDate.isBefore(oldestGap)) {
                        oldestGap = latestDate;
                    }
                } else {
                    anyMissing = true;
                    log.info("MF {} has no history, will backfill", symbol);
                }
            }

            long daysSinceOldest = ChronoUnit.DAYS.between(oldestGap, today);

            if (daysSinceOldest < MIN_GAP_DAYS_TO_BACKFILL && !anyMissing) {
                log.info("Mutual fund history is up to date");
                return;
            }

            LocalDate from;
            if (anyMissing) {
                from = today.minusDays(Math.min(MAX_INITIAL_BACKFILL_DAYS, 90));
                log.info("MF backfill: initial fetch from {}", from);
            } else {
                from = oldestGap.plusDays(1);
                log.info("MF backfill: gap of {} days, filling from {}", daysSinceOldest, from);
            }

            amfiHistoricalService.triggerBackfill(from, today);
        } catch (Exception e) {
            log.error("MF backfill error: {}", e.getMessage(), e);
        }
    }

    private void backfillNetWorthHistory() {
        try {
            userRepository.findAll().forEach(user -> {
                try {
                    netWorthHistoryService.snapshotNetWorth(user.getId());
                    log.debug("Snapshot net worth for user {}", user.getId());
                } catch (Exception e) {
                    log.warn("Failed to snapshot net worth for user {}: {}",
                            user.getId(), e.getMessage());
                }
            });
            log.info("Net worth snapshots taken for all users");
        } catch (Exception e) {
            log.error("Net worth backfill error: {}", e.getMessage(), e);
        }
    }

    @Async
    public void triggerManualBackfill() {
        log.info("Manual backfill triggered");
        backfillEquityHistory();
        backfillMutualFundHistory();
        backfillNetWorthHistory();
    }
}
