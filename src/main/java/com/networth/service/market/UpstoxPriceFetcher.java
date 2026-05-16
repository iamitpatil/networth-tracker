package com.networth.service.market;

import com.networth.model.dto.PriceData;
import com.networth.repository.HoldingRepository;
import com.networth.repository.SymbolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpstoxPriceFetcher {

    private final RestTemplate restTemplate;
    private final SymbolRepository symbolRepository;
    private final HoldingRepository holdingRepository;

    @Value("${market.data.upstox.access-token:}")
    private String accessToken;

    @Value("${market.data.upstox.base-url:https://api.upstox.com/v3}")
    private String baseUrl;

    public PriceData fetchPriceData(String symbol) {
        if (accessToken == null || accessToken.isBlank()) return null;

        String isin = resolveIsin(symbol);
        if (isin == null) return null;

        return fetchFromUpstox(isin);
    }

    public BigDecimal fetchIndianStockPrice(String symbol) {
        PriceData pd = fetchPriceData(symbol);
        return pd != null ? pd.getPrice() : null;
    }

    private String resolveIsin(String symbol) {
        String cleanSymbol = symbol.replace(".NS", "").replace(".BSE", "");
        String nsSymbol = cleanSymbol + ".NS";

        Optional<String> fromSymbol = symbolRepository.findById(nsSymbol)
                .map(com.networth.model.entity.Symbol::getIsin)
                .filter(isin -> !isin.isBlank());
        if (fromSymbol.isPresent()) {
            log.debug("ISIN for {} from symbols: {}", symbol, fromSymbol.get());
            return fromSymbol.get();
        }

        Optional<String> fromHolding = holdingRepository.findIsinBySymbol(symbol);
        if (fromHolding.isPresent()) {
            log.debug("ISIN for {} from holdings: {}", symbol, fromHolding.get());
            return fromHolding.get();
        }
        fromHolding = holdingRepository.findIsinBySymbol(cleanSymbol);
        if (fromHolding.isPresent()) {
            log.debug("ISIN for {} from holdings: {}", cleanSymbol, fromHolding.get());
            return fromHolding.get();
        }

        log.warn("No ISIN found for symbol: {}", symbol);
        return null;
    }

    @SuppressWarnings("unchecked")
    private PriceData fetchFromUpstox(String isin) {
        try {
            String instrumentKey = "NSE_EQ|" + isin;
            String url = baseUrl + "/market-quote/ltp?instrument_key=" + instrumentKey;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("Authorization", "Bearer " + accessToken);

            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if (data != null) {
                    String responseKey = "NSE_EQ:" + isin;
                    Map<String, Object> quote = null;

                    if (data.containsKey(responseKey)) {
                        quote = (Map<String, Object>) data.get(responseKey);
                    } else if (data.size() > 0) {
                        quote = (Map<String, Object>) data.values().iterator().next();
                    }

                    if (quote != null) {
                        Number lastPrice = (Number) quote.get("last_price");
                        Number prevClose = (Number) quote.get("cp");

                        if (lastPrice != null) {
                            BigDecimal price = BigDecimal.valueOf(lastPrice.doubleValue());
                            BigDecimal previousClose = prevClose != null ? BigDecimal.valueOf(prevClose.doubleValue()) : null;
                            return PriceData.builder().price(price).previousClose(previousClose).build();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch price from Upstox for ISIN {}: {}", isin, e.getMessage());
        }
        return null;
    }
}
