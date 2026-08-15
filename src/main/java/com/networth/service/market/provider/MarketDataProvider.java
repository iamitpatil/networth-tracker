package com.networth.service.market.provider;

import com.networth.model.dto.PriceData;
import com.networth.model.enums.AssetType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Unified interface for all market data sources.
 * Each provider implements only the data types it supports.
 * Default methods return null/empty so providers only override what they handle.
 */
public interface MarketDataProvider {

    /** Unique name used in config, e.g. "upstox", "yahoo", "nse" */
    String getName();

    /** Which data types this provider can supply */
    Set<MarketDataType> supportedTypes();

    /** Fetch current price for a symbol */
    default BigDecimal fetchPrice(String symbol, AssetType assetType) {
        return null;
    }

    /** Fetch price with previous close */
    default PriceData fetchPriceData(String symbol, AssetType assetType) {
        BigDecimal price = fetchPrice(symbol, assetType);
        return price != null ? PriceData.builder().price(price).build() : null;
    }

    /** Fetch mutual fund NAV by ISIN */
    default BigDecimal fetchMfNav(String isin) {
        return null;
    }

    /** Fetch dividend events for a symbol */
    default List<DividendEvent> fetchDividends(String symbol) {
        return List.of();
    }

    /**
     * Fetch every corporate action for a symbol — dividends, bonuses, splits, demergers.
     *
     * <p>Null by default, which for this method means "no answer from me". That is the same signal a failed
     * request gives, and the resolver relies on it: a provider that cannot supply corporate actions falls
     * through to the next, and only when none answers does the caller see null and know to retry. An empty
     * list is reserved for a provider that did answer and reported nothing, which lets the event sync mark
     * the symbol done instead of asking again forever.
     */
    default List<CorporateActionEvent> fetchCorporateActions(String symbol) {
        return null;
    }

    /** Fetch news articles */
    default List<NewsItem> fetchNews(String query, int limit) {
        return List.of();
    }
}
