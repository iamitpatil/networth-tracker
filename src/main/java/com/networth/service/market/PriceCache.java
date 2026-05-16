package com.networth.service.market;

import com.networth.model.entity.MarketPrice;
import com.networth.model.enums.AssetType;
import com.networth.repository.MarketPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceCache {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MarketPriceRepository marketPriceRepository;

    private static final String PRICE_KEY_PREFIX = "market:price:";
    private static final long INTRADAY_TTL_MINUTES = 15;
    private static final long NAV_TTL_HOURS = 24;

    public void cachePrice(String symbol, AssetType assetType, BigDecimal price) {
        String key = buildKey(symbol, assetType);
        redisTemplate.opsForValue().set(key, price.toString(), getTTL(assetType), TimeUnit.MINUTES);
    }

    public BigDecimal getCachedPrice(String symbol, AssetType assetType) {
        String key = buildKey(symbol, assetType);
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public BigDecimal getLatestDbPrice(String symbol, AssetType assetType) {
        Optional<MarketPrice> latest = marketPriceRepository.findLatestPrice(symbol, assetType);
        return latest.map(MarketPrice::getPrice).orElse(null);
    }

    public List<MarketPrice> getHistoricalPrices(String symbol, AssetType assetType, LocalDate startDate, LocalDate endDate) {
        return marketPriceRepository.findBySymbolAndAssetTypeAndPriceDateBetweenOrderByPriceDate(
                symbol, assetType, startDate, endDate);
    }

    public void savePrice(String symbol, AssetType assetType, BigDecimal price, String source) {
        MarketPrice marketPrice = MarketPrice.builder()
                .symbol(symbol)
                .assetType(assetType)
                .priceDate(LocalDate.now())
                .price(price)
                .source(source)
                .build();

        try {
            marketPriceRepository.save(marketPrice);
        } catch (Exception e) {
            log.warn("Duplicate price entry for {} on {}: {}", symbol, LocalDate.now(), e.getMessage());
        }

        cachePrice(symbol, assetType, price);
    }

    private String buildKey(String symbol, AssetType assetType) {
        return PRICE_KEY_PREFIX + symbol + ":" + assetType;
    }

    private int getTTL(AssetType assetType) {
        return assetType == AssetType.MUTUAL_FUND ? (int) (NAV_TTL_HOURS * 60) : (int) INTRADAY_TTL_MINUTES;
    }
}
