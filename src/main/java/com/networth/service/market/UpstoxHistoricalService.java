package com.networth.service.market;

import com.networth.model.entity.Holding;
import com.networth.model.entity.StockPriceHistory;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.StockPriceHistoryRepository;
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
import java.util.ArrayList;
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
                if (h.getIsin() != null && !h.getIsin().isBlank()) {
                    total += backfillSymbol(h.getSymbol(), h.getIsin(), fromDate, toDate);
                }
            }
        }
        log.info("Backfilled {} stock price history records", total);
        return total;
    }

    @Transactional
    public int backfillSymbol(String symbol, String isin, LocalDate fromDate, LocalDate toDate) {
        if (accessToken == null || accessToken.isBlank()) return 0;

        try {
            String instrumentKey = "NSE_EQ|" + isin;
            String toStr = toDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String fromStr = fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String url = baseUrl + "/historical-candle/" + instrumentKey + "/days/1/" + toStr + "/" + fromStr;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("Authorization", "Bearer " + accessToken);

            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if (data != null) {
                    List<List<Object>> candles = (List<List<Object>>) data.get("candles");
                    if (candles != null) {
                        int saved = 0;
                        for (List<Object> candle : candles) {
                            if (candle.size() < 5) continue;
                            try {
                                String ts = candle.get(0).toString();
                                LocalDate priceDate = LocalDate.parse(ts.substring(0, 10));
                                BigDecimal open = toBigDecimal(candle.get(1));
                                BigDecimal high = toBigDecimal(candle.get(2));
                                BigDecimal low = toBigDecimal(candle.get(3));
                                BigDecimal close = toBigDecimal(candle.get(4));
                                Long volume = candle.size() > 5 && candle.get(5) != null
                                        ? ((Number) candle.get(5)).longValue() : null;

                                Optional<StockPriceHistory> existing = historyRepository
                                        .findBySymbolAndPriceDate(symbol, priceDate);
                                if (existing.isEmpty()) {
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
                                }
                            } catch (Exception e) {
                                log.warn("Failed to parse candle for {}: {}", symbol, e.getMessage());
                            }
                        }
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
     * Daily backfill at 3 AM. Looks back 7 days for missed updates.
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

    /**
     * Backfill from a specific date until today.
     * Used by StartupBackfillService when gaps are detected.
     */
    @Transactional
    public int backfillFromDate(LocalDate fromDate) {
        return backfillAll(fromDate, LocalDate.now());
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
