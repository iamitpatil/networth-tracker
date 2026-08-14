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

    private final ProviderRateLimiter rateLimiter;
    private final ProviderRateLimits rateLimits;

    public MarketDataResolver(List<MarketDataProvider> providers,
                             ProviderRateLimiter rateLimiter,
                             ProviderRateLimits rateLimits) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(MarketDataProvider::getName, p -> p));
        this.rateLimiter = rateLimiter;
        this.rateLimits = rateLimits;
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
        // Printed at boot so the limits in force are discoverable from the log rather than only
        // from the source or the status endpoint.
        log.info("Provider rate limits enforced:");
        rateLimits.getProviders().forEach((name, limit) ->
                log.info("  {} -> {} (documented: {})", name,
                        limit.windows().stream().map(ProviderRateLimits.Window::label)
                                .collect(Collectors.joining(", ")),
                        limit.getDocumented()));
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

    /**
     * Walks a chain and returns the first usable result.
     *
     * <p>All five public methods below funnel through here, so the rate limit, the fallback and the
     * error handling are stated once. Previously each repeated the same loop, which is how a policy
     * like rate limiting ends up applied to some data types and not others.
     *
     * <p>A provider at its rate limit is <b>skipped</b>, not waited for. That composes with the
     * fallback chain: when Upstox is exhausted the request goes to Yahoo instead of stalling, which
     * is the whole reason the chain exists.
     */
    private <T> T firstResult(MarketDataType type, String what,
                              java.util.function.Function<MarketDataProvider, T> call,
                              java.util.function.Predicate<T> usable) {
        return firstResult(type, what, call, usable, java.time.Duration.ZERO);
    }

    /**
     * As above, but willing to wait up to {@code maxWait} for a provider's slot.
     *
     * <p>{@code Duration.ZERO} is the live-path behaviour and what every caller above passes: skip a
     * throttled provider and try the next. A longer wait is for batch callers that have no alternative
     * provider and are not serving a user.
     *
     * <p>Waiting is opt-in per call site rather than resolver policy on purpose. A single loop that
     * asked for 24 symbols through the non-waiting path is what broke dividend calculation: nse allows
     * 1/s and 10/min, yahoo 5/s, so the loop drained both per-second buckets in its first 165ms and
     * about eighteen symbols were skipped without a request ever being issued — silently, at debug
     * level. Making the resolver wait for everyone would have fixed that and broken the five-minute
     * price sweep instead, which shares this method and must keep falling through.
     */
    private <T> T firstResult(MarketDataType type, String what,
                              java.util.function.Function<MarketDataProvider, T> call,
                              java.util.function.Predicate<T> usable,
                              java.time.Duration maxWait) {
        for (MarketDataProvider provider : getProviders(type)) {
            if (!rateLimiter.acquire(provider.getName(), maxWait)) {
                log.debug("Skipping {} for {} {}: at its rate limit", provider.getName(), type, what);
                continue;
            }
            try {
                T result = call.apply(provider);
                if (usable.test(result)) {
                    log.debug("{} {} resolved by {}", type, what, provider.getName());
                    return result;
                }
            } catch (Exception e) {
                log.warn("Provider {} failed for {} {}: {}", provider.getName(), type, what, e.getMessage());
            }
        }
        return null;
    }

    public BigDecimal getPrice(String symbol, AssetType assetType) {
        return firstResult(MarketDataType.PRICE, symbol,
                p -> p.fetchPrice(symbol, assetType), Objects::nonNull);
    }

    public PriceData getPriceData(String symbol, AssetType assetType) {
        return firstResult(MarketDataType.PRICE, symbol,
                p -> p.fetchPriceData(symbol, assetType), Objects::nonNull);
    }

    public BigDecimal getMfNav(String isin) {
        return firstResult(MarketDataType.MF_NAV, isin,
                p -> p.fetchMfNav(isin), Objects::nonNull);
    }

    public List<DividendEvent> getDividends(String symbol) {
        List<DividendEvent> events = firstResult(MarketDataType.DIVIDEND, symbol,
                p -> p.fetchDividends(symbol), list -> list != null && !list.isEmpty());
        return events != null ? events : List.of();
    }

    /**
     * Dividend events for one symbol, waiting for a provider slot instead of skipping.
     *
     * <p>For the event sync only, which fetches each symbol once into {@code symbol_events} and has no
     * alternative provider worth falling through to — Yahoo answers every request with an IP-level
     * {@code 429 Edge: Too Many Requests}, so NSE's 1/s and 10/min is the real budget and a skip means
     * that symbol simply gets no dividends. Pausing for a slot is what makes the sync complete.
     *
     * <p>Never call this from a request thread serving a page: at 10/min the eleventh symbol waits the
     * better part of a minute.
     *
     * <p>Unlike {@link #getDividends}, an <b>empty list and null mean different things</b>, and the
     * caller depends on the difference:
     *
     * <ul>
     *   <li>a list, possibly empty — a provider answered, so an empty list means this company has
     *       announced no dividends and the sync can mark it done and never ask again</li>
     *   <li>{@code null} — no provider could be reached at all, so the sync must leave the symbol
     *       unmarked and retry it next run</li>
     * </ul>
     *
     * Conflating the two is how a provider outage would masquerade as "this stock pays nothing" and
     * leave the store permanently empty. It is also why the usability test here accepts an empty list
     * rather than falling through on one: NSE is the authoritative source for Indian corporate actions,
     * so its considered "nothing" is an answer, and trying a blocked Yahoo afterwards only wastes a call.
     *
     * @return the events, an empty list if a provider reported none, or null if none was reachable
     */
    public List<DividendEvent> getDividendsWaiting(String symbol, java.time.Duration maxWait) {
        return firstResult(MarketDataType.DIVIDEND, symbol,
                p -> p.fetchDividends(symbol), Objects::nonNull, maxWait);
    }

    public List<NewsItem> getNews(String query, int limit) {
        List<NewsItem> items = firstResult(MarketDataType.NEWS, query,
                p -> p.fetchNews(query, limit), list -> list != null && !list.isEmpty());
        return items != null ? items : List.of();
    }

    /** Get the name of the first available provider for a given data type */
    public String getSourceName(MarketDataType type) {
        List<MarketDataProvider> providers = getProviders(type);
        return providers.isEmpty() ? "UNKNOWN" : providers.get(0).getName().toUpperCase();
    }
}
