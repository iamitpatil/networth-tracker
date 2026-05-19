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
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpstoxHistoricalService {

    private final RestTemplate restTemplate;
    private final HoldingRepository holdingRepository;
    private final StockPriceHistoryRepository historyRepository;
    private final SymbolRepository symbolRepository;

    @Value("${market.data.upstox.access-token:}")
    private String accessToken;

    @Value("${market.data.upstox.base-url:https://api.upstox.com/v3}")
    private String baseUrl;

    @Transactional
    public int backfillAll(LocalDate fromDate, LocalDate toDate) {
        List<Holding> holdings = holdingRepository.findAll();
        int total = 0;
        for (Holding h : holdings) {
            if (h.getAssetType() == AssetType.EQUITY || h.getAssetType() == AssetType.ETF) {
                String isin = resolveAndPersistIsin(h);
                if (isin != null && !isin.isBlank()) {
                    total += backfillSymbol(h.getSymbol(), isin, fromDate, toDate);
                }
            }
        }
        log.info("Backfilled {} stock price history records", total);
        return total;
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
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledDailyBackfill() {
        if (accessToken == null || accessToken.isBlank()) return;
        log.info("Running daily price history backfill...");
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(7);
            int count = backfillAll(from, to);
            log.info("Daily backfill complete: {} records added", count);
        } catch (Exception e) {
            log.error("Daily backfill failed: {}", e.getMessage());
        }
    }

    @Transactional
    public int backfillFromDate(LocalDate fromDate) {
        return backfillAll(fromDate, LocalDate.now());
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
