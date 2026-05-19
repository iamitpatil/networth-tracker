package com.networth.service.market.provider;

import com.networth.model.dto.PriceData;
import com.networth.model.enums.AssetType;
import com.networth.service.market.AlphaVantagePriceFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component("alpha-vantage")
@RequiredArgsConstructor
public class AlphaVantageProvider implements MarketDataProvider {

    private final AlphaVantagePriceFetcher priceFetcher;

    @Override
    public String getName() {
        return "alpha-vantage";
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
        return priceFetcher.fetchPriceData(symbol.replace(".NS", ".BSE"));
    }
}
