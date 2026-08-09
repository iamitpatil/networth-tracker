package com.networth.service.market;

import com.networth.model.entity.MarketPrice;
import com.networth.model.enums.AssetType;
import com.networth.repository.MarketPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceCache {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MarketPriceRepository marketPriceRepository;
    private final PriceFreshnessPolicy freshnessPolicy;

    private static final String PRICE_KEY_PREFIX = "market:price:";

    /**
     * Caches a price under a TTL that expires when {@link PriceFreshnessPolicy} stops vouching for it.
     *
     * <p>Which is why the confirmation time is a parameter rather than assumed to be now: a price
     * re-cached from a stored row is already part-way through its life, and starting a full TTL from
     * the moment of caching would let a 14-minute-old equity quote be served for another 15. The
     * previous fixed TTLs -- 15 minutes for everything, 24 hours for a NAV -- were an independent
     * guess at the same question, and the NAV one was wrong: publication comes round every 23 hours.
     *
     * <p>A price the policy already considers stale is not cached at all. Caching it would hide the
     * staleness from the next reader and suppress the fetch that would fix it.
     */
    public void cachePrice(String symbol, AssetType assetType, BigDecimal price, Instant confirmedAt) {
        Duration ttl = freshnessPolicy.remainingFreshness(assetType, confirmedAt, Instant.now());
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        redisTemplate.opsForValue().set(buildKey(symbol, assetType), price.toString(), ttl);
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

    /**
     * The most recent stored price with its metadata, so a caller can see how old it is.
     *
     * <p>{@link #getLatestDbPrice} returns the number alone, which is all most callers want. Anything
     * deciding whether to re-fetch needs {@code updatedAt} as well, and a bare {@code BigDecimal}
     * cannot carry it.
     */
    public Optional<MarketPrice> getLatestPriceRecord(String symbol, AssetType assetType) {
        return marketPriceRepository.findLatestPrice(symbol, assetType);
    }

    public List<MarketPrice> getHistoricalPrices(String symbol, AssetType assetType, LocalDate startDate, LocalDate endDate) {
        return marketPriceRepository.findBySymbolAndAssetTypeAndPriceDateBetweenOrderByPriceDate(
                symbol, assetType, startDate, endDate);
    }

    /**
     * Records a price a provider has just returned, as the day's price for this symbol.
     *
     * <p>An upsert, because {@code market_prices} keeps one row per symbol per day and an intraday
     * refresh has to replace it. The previous version built a fresh entity and let
     * {@code save()} sort it out, which worked -- an assigned composite id makes Spring Data merge
     * rather than persist -- but only by accident of that heuristic, and it left the day's
     * {@code updated_at} unrecorded.
     */
    public void savePrice(String symbol, AssetType assetType, BigDecimal price, String source) {
        LocalDate today = LocalDate.now(MarketCalendar.ZONE);
        MarketPrice marketPrice = marketPriceRepository
                .findBySymbolAndAssetTypeAndDate(symbol, assetType, today)
                .orElseGet(() -> MarketPrice.builder()
                        .symbol(symbol)
                        .assetType(assetType)
                        .priceDate(today)
                        .build());

        marketPrice.setPrice(price);
        marketPrice.setSource(source);
        // Set on both paths: a provider returning an unchanged number has still confirmed it, and
        // that is what freshness is measured against.
        Instant confirmedAt = Instant.now();
        marketPrice.setUpdatedAt(confirmedAt);

        try {
            marketPriceRepository.save(marketPrice);
        } catch (DataIntegrityViolationException e) {
            // Only reachable when another thread inserted today's row between the read above and this
            // write -- the sweep and a Holdings page load refreshing the same symbol at once. Their
            // price is from the same moment and equally good, and Redis is updated either way, so
            // there is nothing to repair.
            log.debug("Concurrent price write for {} on {}; keeping the other writer's row", symbol, today);
        }

        cachePrice(symbol, assetType, price, confirmedAt);
    }

    private String buildKey(String symbol, AssetType assetType) {
        return PRICE_KEY_PREFIX + symbol + ":" + assetType;
    }
}
