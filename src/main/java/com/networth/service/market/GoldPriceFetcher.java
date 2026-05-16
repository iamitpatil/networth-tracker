package com.networth.service.market;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoldPriceFetcher {

    private final RestTemplate restTemplate;
    private final AlphaVantagePriceFetcher alphaVantagePriceFetcher;

    public BigDecimal fetchGoldPricePerGram() {
        try {
            BigDecimal usdPerOz = fetchFromGoldApi();
            if (usdPerOz == null) {
                usdPerOz = fetchFromAlphaVantage();
            }
            if (usdPerOz == null) {
                usdPerOz = fetchFromYahoo();
            }
            if (usdPerOz == null) return null;

            BigDecimal usdInr = fetchUsdInr();
            if (usdInr == null) usdInr = new BigDecimal("87");

            BigDecimal inrPerGram = usdPerOz
                    .divide(new BigDecimal("31.1035"), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(usdInr);
            return inrPerGram.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Failed to fetch gold price: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal fetchFromGoldApi() {
        try {
            String url = "https://api.gold-api.com/price/XAU";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object price = response.getBody().get("price");
                if (price != null) {
                    BigDecimal p = new BigDecimal(price.toString());
                    log.info("Gold price via gold-api.com: ${}/oz", p);
                    return p;
                }
            }
        } catch (Exception e) {
            log.debug("gold-api.com failed: {}", e.getMessage());
        }
        return null;
    }

    private BigDecimal fetchFromAlphaVantage() {
        try {
            BigDecimal p = alphaVantagePriceFetcher.fetchPrice("GOLDETF.NS");
            if (p != null) {
                log.info("Gold price via Alpha Vantage GOLDETF.NS: ₹{}", p);
                return p;
            }
        } catch (Exception e) {
            log.debug("Alpha Vantage gold failed: {}", e.getMessage());
        }
        return null;
    }

    private BigDecimal fetchFromYahoo() {
        return null;
    }

    private BigDecimal fetchUsdInr() {
        try {
            BigDecimal rate = alphaVantagePriceFetcher.fetchPrice("INR=X");
            if (rate != null) return rate;
        } catch (Exception e) {
            log.debug("Alpha Vantage INR=X failed: {}", e.getMessage());
        }
        return null;
    }
}
