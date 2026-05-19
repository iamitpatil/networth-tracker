package com.networth.service.market.provider;

import com.networth.model.dto.PriceData;
import com.networth.model.enums.AssetType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves market data requests by trying configured providers in order.
 *
 * Provider chains are configured in application.properties:
 *   market.providers.price=upstox,yahoo,alpha-vantage
 *   market.providers.mf-nav=upstox-mf,amfi
 *   market.providers.dividend=nse,yahoo
 *   market.providers.news=google
 *
 * Each provider is tried in sequence; first non-null/non-empty result wins.
 */
@Service
@Slf4j
public class MarketDataResolver {

    private final Map<String, MarketDataProvider> providerMap;

    @Value("${market.providers.price:upstox,yahoo,alpha-vantage}")
    private String priceChain;

    @Value("${market.providers.mf-nav:upstox-mf,amfi}")
    private String mfNavChain;

    @Value("${market.providers.dividend:nse,yahoo}")
    private String dividendChain;

    @Value("${market.providers.news:google}")
    private String newsChain;

    private final Map<MarketDataType, List<MarketDataProvider>> chains = new EnumMap<>(MarketDataType.class);

    public MarketDataResolver(List<MarketDataProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(MarketDataProvider::getName, p -> p));
    }

    @PostConstruct
    public void init() {
        chains.put(MarketDataType.PRICE, buildChain(priceChain, MarketDataType.PRICE));
        chains.put(MarketDataType.MF_NAV, buildChain(mfNavChain, MarketDataType.MF_NAV));
        chains.put(MarketDataType.DIVIDEND, buildChain(dividendChain, MarketDataType.DIVIDEND));
        chains.put(MarketDataType.NEWS, buildChain(newsChain, MarketDataType.NEWS));

        log.info("Market data provider chains configured:");
        chains.forEach((type, providers) ->
                log.info("  {} -> [{}]", type,
                        providers.stream().map(MarketDataProvider::getName).collect(Collectors.joining(" -> ")))
        );
    }

    private List<MarketDataProvider> buildChain(String configValue, MarketDataType type) {
        if (configValue == null || configValue.isBlank()) return List.of();

        List<MarketDataProvider> chain = new ArrayList<>();
        for (String name : configValue.split(",")) {
            String trimmed = name.trim();
            MarketDataProvider provider = providerMap.get(trimmed);
            if (provider == null) {
                log.warn("Provider '{}' not found, skipping for {} chain", trimmed, type);
                continue;
            }
            if (!provider.supportedTypes().contains(type)) {
                log.warn("Provider '{}' does not support {}, skipping", trimmed, type);
                continue;
            }
            chain.add(provider);
        }
        return chain;
    }

    public List<MarketDataProvider> getProviders(MarketDataType type) {
        return chains.getOrDefault(type, List.of());
    }

    // --- Convenience methods that walk the chain ---

    public BigDecimal getPrice(String symbol, AssetType assetType) {
        for (MarketDataProvider provider : getProviders(MarketDataType.PRICE)) {
            try {
                BigDecimal price = provider.fetchPrice(symbol, assetType);
                if (price != null) {
                    log.debug("Price for {} from {}: {}", symbol, provider.getName(), price);
                    return price;
                }
            } catch (Exception e) {
                log.warn("Provider {} failed for price {}: {}", provider.getName(), symbol, e.getMessage());
            }
        }
        return null;
    }

    public PriceData getPriceData(String symbol, AssetType assetType) {
        for (MarketDataProvider provider : getProviders(MarketDataType.PRICE)) {
            try {
                PriceData data = provider.fetchPriceData(symbol, assetType);
                if (data != null) {
                    log.debug("PriceData for {} from {}", symbol, provider.getName());
                    return data;
                }
            } catch (Exception e) {
                log.warn("Provider {} failed for priceData {}: {}", provider.getName(), symbol, e.getMessage());
            }
        }
        return null;
    }

    public BigDecimal getMfNav(String isin) {
        for (MarketDataProvider provider : getProviders(MarketDataType.MF_NAV)) {
            try {
                BigDecimal nav = provider.fetchMfNav(isin);
                if (nav != null) {
                    log.debug("MF NAV for {} from {}: {}", isin, provider.getName(), nav);
                    return nav;
                }
            } catch (Exception e) {
                log.warn("Provider {} failed for MF NAV {}: {}", provider.getName(), isin, e.getMessage());
            }
        }
        return null;
    }

    public List<DividendEvent> getDividends(String symbol) {
        for (MarketDataProvider provider : getProviders(MarketDataType.DIVIDEND)) {
            try {
                List<DividendEvent> events = provider.fetchDividends(symbol);
                if (events != null && !events.isEmpty()) {
                    log.debug("{} dividend events for {} from {}", events.size(), symbol, provider.getName());
                    return events;
                }
            } catch (Exception e) {
                log.warn("Provider {} failed for dividends {}: {}", provider.getName(), symbol, e.getMessage());
            }
        }
        return List.of();
    }

    public List<NewsItem> getNews(String query, int limit) {
        for (MarketDataProvider provider : getProviders(MarketDataType.NEWS)) {
            try {
                List<NewsItem> items = provider.fetchNews(query, limit);
                if (items != null && !items.isEmpty()) {
                    log.debug("{} news items for '{}' from {}", items.size(), query, provider.getName());
                    return items;
                }
            } catch (Exception e) {
                log.warn("Provider {} failed for news '{}': {}", provider.getName(), query, e.getMessage());
            }
        }
        return List.of();
    }

    /** Get the name of the first available provider for a given data type */
    public String getSourceName(MarketDataType type) {
        List<MarketDataProvider> providers = getProviders(type);
        return providers.isEmpty() ? "UNKNOWN" : providers.get(0).getName().toUpperCase();
    }
}
