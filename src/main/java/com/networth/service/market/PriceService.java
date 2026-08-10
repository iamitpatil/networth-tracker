package com.networth.service.market;

import com.networth.model.dto.PriceData;
import com.networth.model.entity.MarketPrice;
import com.networth.model.enums.AssetType;
import com.networth.service.market.provider.MarketDataResolver;
import com.networth.service.market.provider.MarketDataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceService {

    private final MarketDataResolver resolver;
    private final GoldPriceFetcher goldPriceFetcher;
    private final NpsNavService npsNavService;
    private final PriceCache priceCache;
    private final PriceFreshnessPolicy freshnessPolicy;

    /**
     * The best available price for a symbol, fetching one only if what is stored is out of date.
     *
     * <p>The age check is the point. Without it this method fell through to "the newest row in
     * {@code market_prices}, whatever its date" and returned it as today's price -- then wrote it back
     * into Redis, so a six-month-old figure became sticky for as long as anyone kept looking at it. A
     * price that old is not a cache miss to paper over; it means nobody has asked a provider in six
     * months.
     *
     * @return the price, or {@code null} when nothing is stored and no provider will answer
     */
    public BigDecimal getCurrentPrice(String symbol, AssetType assetType) {
        BigDecimal cached = priceCache.getCachedPrice(symbol, assetType);
        if (cached != null) {
            // Redis entries expire inside the policy's window (see PriceCache#cachePrice), so a hit is
            // fresh by construction and needs no age check of its own.
            return cached;
        }

        Optional<MarketPrice> stored = priceCache.getLatestPriceRecord(symbol, assetType);
        BigDecimal storedPrice = stored.map(MarketPrice::getPrice).orElse(null);
        Instant confirmedAt = stored.map(MarketPrice::getUpdatedAt).orElse(null);

        // Also covers the types no provider can price: the policy calls those never stale, so an EPF
        // balance is served from its row and a portfolio of fixed deposits makes no outbound requests.
        if (!freshnessPolicy.isStale(assetType, confirmedAt)) {
            if (storedPrice != null) {
                priceCache.cachePrice(symbol, assetType, storedPrice, confirmedAt);
            }
            return storedPrice;
        }

        BigDecimal livePrice = fetchWithFallback(symbol, assetType);
        if (livePrice != null) {
            // Records the confirmation time and writes through to Redis, so the next reader gets a
            // cache hit rather than a second provider call for the same number.
            priceCache.savePrice(symbol, assetType, livePrice, sourceFor(assetType));
            return livePrice;
        }

        // No provider answered. A stale stored price still beats no price at all for a portfolio
        // total, but it is deliberately not re-cached: caching it would suppress the next attempt and
        // pin the old number in place, which is how the original bug survived.
        if (storedPrice != null) {
            log.debug("Serving a stale price for {} ({}), last confirmed {}", symbol, assetType, confirmedAt);
        }
        return storedPrice;
    }

    /**
     * Fetches and stores a price unconditionally, for the explicit "refresh now" path where the user
     * has asked for a provider call and is entitled to get one.
     *
     * @return whether a provider answered and the price was stored
     */
    public boolean refreshPrice(String symbol, AssetType assetType) {
        BigDecimal price = fetchWithFallback(symbol, assetType);
        if (price == null) {
            return false;
        }
        priceCache.savePrice(symbol, assetType, price, sourceFor(assetType));
        return true;
    }

    /**
     * Fetches a price only if the stored one is out of date.
     *
     * <p>What the scheduled sweep and the Holdings page both want. The page previously forced a
     * provider call for every holding on every load, so a portfolio of thirty holdings sent thirty
     * requests to be told thirty times what it already knew -- and did so at 03:00 as readily as at
     * midday.
     *
     * @return whether a new price was fetched and stored; {@code false} means either that the stored
     *         price was still current or that no provider answered
     */
    public boolean refreshPriceIfStale(String symbol, AssetType assetType) {
        Instant confirmedAt = lastConfirmedAt(symbol, assetType).orElse(null);
        if (!freshnessPolicy.isStale(assetType, confirmedAt)) {
            return false;
        }
        return refreshPrice(symbol, assetType);
    }

    /** When a provider last confirmed this symbol's price, if one ever has. */
    public Optional<Instant> lastConfirmedAt(String symbol, AssetType assetType) {
        return priceCache.getLatestPriceRecord(symbol, assetType).map(MarketPrice::getUpdatedAt);
    }

    public BigDecimal getPreviousClose(String symbol, AssetType assetType) {
        if (assetType == AssetType.EQUITY || assetType == AssetType.ETF) {
            PriceData pd = resolver.getPriceData(symbol, assetType);
            if (pd != null && pd.getPreviousClose() != null) {
                return pd.getPreviousClose();
            }
        }
        return null;
    }

    private BigDecimal fetchWithFallback(String symbol, AssetType assetType) {
        if (assetType == AssetType.MUTUAL_FUND) {
            return resolver.getMfNav(symbol);
        }
        if (assetType == AssetType.GOLD || assetType == AssetType.SGB) {
            return goldPriceFetcher.fetchGoldPricePerGram();
        }
        if (assetType == AssetType.NPS) {
            // A pension fund has exactly one price source, and it is not the equity chain. NPS used to
            // fall through to the line below, where Upstox, Yahoo and Alpha Vantage were each asked for
            // a quote on a symbol like SM001001 and each failed — three provider calls per sweep, per
            // holding, forever, to produce nothing. PriceFreshnessPolicy already declares NPS as DAILY,
            // so the freshness machinery has always expected this to be priceable.
            //
            // The symbol is the scheme code: SymbolValidator only accepts an NPS holding whose symbol is
            // a listed scheme, and getEffectiveSymbolForPricing returns it unchanged. Loosening either
            // would break this silently.
            return npsNavService.getLatestNav(symbol);
        }
        return resolver.getPrice(symbol, assetType);
    }

    /**
     * What to record as the price's origin.
     *
     * <p>NPS is named explicitly because {@link #getDataType} maps it to {@code PRICE}, and the source
     * name for {@code PRICE} is the head of the equity chain — which would stamp an NPS NAV as having
     * come from Upstox.
     */
    private String sourceFor(AssetType assetType) {
        if (assetType == AssetType.NPS) {
            return "NPSNAV";
        }
        return resolver.getSourceName(getDataType(assetType));
    }

    private MarketDataType getDataType(AssetType assetType) {
        return assetType == AssetType.MUTUAL_FUND ? MarketDataType.MF_NAV : MarketDataType.PRICE;
    }
}
