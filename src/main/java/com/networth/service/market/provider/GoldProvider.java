package com.networth.service.market.provider;

import com.networth.model.enums.AssetType;
import com.networth.service.market.GoldPriceFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component("gold")
@RequiredArgsConstructor
public class GoldProvider implements MarketDataProvider {

    private final GoldPriceFetcher goldPriceFetcher;

    @Override
    public String getName() {
        return "gold";
    }

    @Override
    public Set<MarketDataType> supportedTypes() {
        return Set.of(MarketDataType.PRICE);
    }

    @Override
    public BigDecimal fetchPrice(String symbol, AssetType assetType) {
        if (assetType == AssetType.GOLD || assetType == AssetType.SGB) {
            return goldPriceFetcher.fetchGoldPricePerGram();
        }
        return null;
    }
}
