package com.networth.service.market.provider;

import com.networth.model.dto.PriceData;
import com.networth.model.enums.AssetType;
import com.networth.service.market.UpstoxPriceFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component("upstox")
@RequiredArgsConstructor
public class UpstoxProvider implements MarketDataProvider {

    private final UpstoxPriceFetcher priceFetcher;

    @Override
    public String getName() {
        return "upstox";
    }

    @Override
    public Set<MarketDataType> supportedTypes() {
        return Set.of(MarketDataType.PRICE);
    }

    @Override
    public BigDecimal fetchPrice(String symbol, AssetType assetType) {
        return priceFetcher.fetchIndianStockPrice(symbol);
    }

    @Override
    public PriceData fetchPriceData(String symbol, AssetType assetType) {
        return priceFetcher.fetchPriceData(symbol);
    }
}
