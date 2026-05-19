package com.networth.service.market.provider;

import com.networth.service.market.AmfiNavFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component("amfi")
@RequiredArgsConstructor
public class AmfiProvider implements MarketDataProvider {

    private final AmfiNavFetcher navFetcher;

    @Override
    public String getName() {
        return "amfi";
    }

    @Override
    public Set<MarketDataType> supportedTypes() {
        return Set.of(MarketDataType.MF_NAV);
    }

    @Override
    public BigDecimal fetchMfNav(String isin) {
        return navFetcher.fetchNav(isin);
    }
}
