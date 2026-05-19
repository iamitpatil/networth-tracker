package com.networth.service.market;

import com.networth.model.dto.PriceData;
import com.networth.model.enums.AssetType;
import com.networth.service.market.provider.MarketDataResolver;
import com.networth.service.market.provider.MarketDataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceService {

    private final MarketDataResolver resolver;
    private final GoldPriceFetcher goldPriceFetcher;
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
            priceCache.savePrice(symbol, assetType, price,
                    resolver.getSourceName(getDataType(assetType)));
        }
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
        return resolver.getPrice(symbol, assetType);
    }

    private MarketDataType getDataType(AssetType assetType) {
        return assetType == AssetType.MUTUAL_FUND ? MarketDataType.MF_NAV : MarketDataType.PRICE;
    }
}
