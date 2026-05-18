package com.networth.service.market;

import com.networth.model.dto.PriceData;
import com.networth.model.enums.AssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceService {

    private final UpstoxPriceFetcher upstoxPriceFetcher;
    private final UpstoxMfFetcher upstoxMfFetcher;
    private final YahooPriceFetcher yahooPriceFetcher;
    private final AlphaVantagePriceFetcher alphaVantagePriceFetcher;
    private final GoldPriceFetcher goldPriceFetcher;
    private final AmfiNavFetcher amfiNavFetcher;
    private final PriceCache priceCache;

    public BigDecimal getCurrentPrice(String symbol, AssetType assetType) {
        BigDecimal cached = priceCache.getCachedPrice(symbol, assetType);
        if (cached != null) return cached;

        BigDecimal dbPrice = priceCache.getLatestDbPrice(symbol, assetType);
        if (dbPrice != null) {
            priceCache.cachePrice(symbol, assetType, dbPrice);
            return dbPrice;
        }

        BigDecimal livePrice = fetchWithFallback(symbol, assetType);
        if (livePrice != null) {
            priceCache.cachePrice(symbol, assetType, livePrice);
        }

        return livePrice;
    }

    public void refreshPrice(String symbol, AssetType assetType) {
        BigDecimal price = fetchWithFallback(symbol, assetType);
        if (price != null) {
            priceCache.savePrice(symbol, assetType, price, getSource(assetType));
        }
    }

    public BigDecimal getPreviousClose(String symbol, AssetType assetType) {
        if (assetType == AssetType.EQUITY || assetType == AssetType.ETF) {
            PriceData pd = upstoxPriceFetcher.fetchPriceData(symbol);
            if (pd != null && pd.getPreviousClose() != null) return pd.getPreviousClose();

            PriceData avPd = alphaVantagePriceFetcher.fetchPriceData(symbol.replace(".NS", ".BSE"));
            if (avPd != null && avPd.getPreviousClose() != null) return avPd.getPreviousClose();
        }
        return null;
    }

    private BigDecimal fetchWithFallback(String symbol, AssetType assetType) {
        BigDecimal price = fetchFromSource(symbol, assetType);
        if (price != null) return price;
        if (assetType == AssetType.EQUITY || assetType == AssetType.ETF) {
            BigDecimal fallback = alphaVantagePriceFetcher.fetchIndianStockPrice(symbol);
            if (fallback != null) {
                log.info("Alpha Vantage fallback for {}: {}", symbol, fallback);
                return fallback;
            }
        }
        return null;
    }

    private BigDecimal fetchFromSource(String symbol, AssetType assetType) {
        return switch (assetType) {
            case MUTUAL_FUND -> {
                // Try Upstox first (single in-memory file, all 12k+ MFs)
                BigDecimal upstoxNav = upstoxMfFetcher.getNav(symbol);
                if (upstoxNav != null) {
                    log.debug("MF NAV from Upstox for {}: {}", symbol, upstoxNav);
                    yield upstoxNav;
                }
                // Fallback to AMFI
                log.debug("MF NAV from AMFI for {} (Upstox unavailable)", symbol);
                yield amfiNavFetcher.fetchNav(symbol);
            }
            case EQUITY, ETF -> {
                BigDecimal upstox = upstoxPriceFetcher.fetchIndianStockPrice(symbol);
                if (upstox != null) {
                    yield upstox;
                }
                yield yahooPriceFetcher.fetchIndianStockPrice(symbol);
            }
            case GOLD, SGB -> goldPriceFetcher.fetchGoldPricePerGram();
            default -> yahooPriceFetcher.fetchPrice(symbol);
        };
    }

    private String getSource(AssetType assetType) {
        return switch (assetType) {
            case MUTUAL_FUND -> upstoxMfFetcher.isAvailable() ? "UPSTOX_MF" : "AMFI";
            case EQUITY, ETF -> "UPSTOX";
            default -> "YAHOO";
        };
    }
}
