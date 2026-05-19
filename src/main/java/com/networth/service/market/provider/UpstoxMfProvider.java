package com.networth.service.market.provider;

import com.networth.service.market.UpstoxMfFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component("upstox-mf")
@RequiredArgsConstructor
public class UpstoxMfProvider implements MarketDataProvider {

    private final UpstoxMfFetcher mfFetcher;

    @Override
    public String getName() {
        return "upstox-mf";
    }

    @Override
    public Set<MarketDataType> supportedTypes() {
        return Set.of(MarketDataType.MF_NAV);
    }

    @Override
    public BigDecimal fetchMfNav(String isin) {
        return mfFetcher.getNav(isin);
    }
}
