package com.networth.service.market.provider;

import com.networth.model.enums.AssetType;
import com.networth.service.market.YahooPriceFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component("yahoo")
@RequiredArgsConstructor
@Slf4j
public class YahooProvider implements MarketDataProvider {

    private final YahooPriceFetcher priceFetcher;
    private final RestTemplate restTemplate;

    private static final String CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=10y&interval=1mo&events=div";

    @Override
    public String getName() {
        return "yahoo";
    }

    @Override
    public Set<MarketDataType> supportedTypes() {
        return Set.of(MarketDataType.PRICE, MarketDataType.DIVIDEND);
    }

    @Override
    public BigDecimal fetchPrice(String symbol, AssetType assetType) {
        return priceFetcher.fetchIndianStockPrice(symbol);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DividendEvent> fetchDividends(String symbol) {
        try {
            String clean = symbol.replaceAll("\\.(NS|BO)$", "");
            String yahooSymbol = clean + ".NS";
            String url = String.format(CHART_URL, yahooSymbol);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return List.of();

            Map<String, Object> chart = (Map<String, Object>) response.get("chart");
            if (chart == null) return List.of();

            List<Map<String, Object>> results = (List<Map<String, Object>>) chart.get("result");
            if (results == null || results.isEmpty()) return List.of();

            Map<String, Object> result = results.get(0);
            Map<String, Object> events = (Map<String, Object>) result.get("events");
            if (events == null) return List.of();

            Map<String, Map<String, Object>> dividends = (Map<String, Map<String, Object>>) events.get("dividends");
            if (dividends == null) return List.of();

            List<DividendEvent> items = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : dividends.entrySet()) {
                Map<String, Object> div = entry.getValue();
                Number dateNum = (Number) div.get("date");
                Number amountNum = (Number) div.get("amount");
                if (dateNum == null || amountNum == null) continue;

                LocalDate exDate = Instant.ofEpochSecond(dateNum.longValue())
                        .atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();

                items.add(DividendEvent.builder()
                        .symbol(symbol)
                        .amountPerShare(BigDecimal.valueOf(amountNum.doubleValue()))
                        .exDate(exDate)
                        .dividendType("Dividend")
                        .source("YAHOO")
                        .description("Dividend Rs." + amountNum + " per share")
                        .build());
            }
            return items;
        } catch (Exception e) {
            log.warn("Yahoo dividend fetch failed for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }
}
