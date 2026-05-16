package com.networth.service.market;

import com.networth.model.enums.AssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class YahooPriceFetcher {

    private final RestTemplate restTemplate;

    @Value("${market.data.yahoo.base-url}")
    private String baseUrl;

    public BigDecimal fetchPrice(String symbol) {
        try {
            String url = baseUrl + "/" + symbol + "?range=1d&interval=1d";

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map chart = (Map) response.getBody().get("chart");
                if (chart != null) {
                    java.util.List result = (java.util.List) chart.get("result");
                    if (result != null && !result.isEmpty()) {
                        Map firstResult = (Map) result.get(0);
                        Map meta = (Map) firstResult.get("meta");
                        if (meta != null) {
                            Object price = meta.get("regularMarketPrice");
                            if (price != null) {
                                return new BigDecimal(price.toString());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch price from Yahoo for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    public BigDecimal fetchIndianStockPrice(String symbol) {
        String clean = symbol.replaceAll("\\.(NS|BO)$", "");
        return fetchPrice(clean + ".NS");
    }
}
