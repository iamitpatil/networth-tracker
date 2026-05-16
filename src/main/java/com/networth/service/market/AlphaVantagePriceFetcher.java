package com.networth.service.market;

import com.networth.model.dto.PriceData;
import com.networth.model.entity.SymbolAlias;
import com.networth.repository.SymbolAliasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlphaVantagePriceFetcher {

    private final RestTemplate restTemplate;
    private final SymbolAliasRepository aliasRepository;

    @Value("${market.data.alpha-vantage.api-key:}")
    private String apiKey;

    @Value("${market.data.alpha-vantage.base-url:https://www.alphavantage.co/query}")
    private String baseUrl;

    public BigDecimal fetchIndianStockPrice(String symbol) {
        if (apiKey == null || apiKey.isBlank()) return null;

        String alphaSymbol = symbol.replace(".NS", ".BSE");
        Optional<SymbolAlias> alias = aliasRepository.findBySymbolAndSource(symbol, "ALPHA_VANTAGE");
        if (alias.isPresent()) {
            alphaSymbol = alias.get().getAlias();
        }

        return fetchPrice(alphaSymbol);
    }

    public BigDecimal fetchPrice(String symbol) {
        PriceData pd = fetchPriceData(symbol);
        return pd != null ? pd.getPrice() : null;
    }

    public PriceData fetchPriceData(String symbol) {
        if (apiKey == null || apiKey.isBlank()) return null;

        try {
            String url = baseUrl + "?function=GLOBAL_QUOTE&symbol=" + symbol + "&apikey=" + apiKey;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map quote = (Map) response.getBody().get("Global Quote");
                if (quote != null) {
                    String priceStr = (String) quote.get("05. price");
                    if (priceStr == null) return null;
                    BigDecimal price = new BigDecimal(priceStr);
                    BigDecimal prevClose = null;
                    String prevStr = (String) quote.get("08. previous close");
                    if (prevStr != null) prevClose = new BigDecimal(prevStr);
                    return PriceData.builder().price(price).previousClose(prevClose).build();
                }
                Map error = (Map) response.getBody().get("Error Message");
                if (error != null) log.warn("Alpha Vantage error for {}: {}", symbol, error);
                Object noteObj = response.getBody().get("Note");
                if (noteObj instanceof String note) log.warn("Alpha Vantage rate limit for {}: {}", symbol, note);
                else if (noteObj instanceof Map note) log.warn("Alpha Vantage rate limit for {}: {}", symbol, note);
            }
        } catch (Exception e) {
            log.error("Failed to fetch price from Alpha Vantage for {}: {}", symbol, e.getMessage());
        }
        return null;
    }
}
