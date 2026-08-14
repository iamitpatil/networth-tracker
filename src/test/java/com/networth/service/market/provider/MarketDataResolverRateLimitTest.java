package com.networth.service.market.provider;

import com.networth.model.enums.AssetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rate limiting has to compose with the fallback chain rather than fight it.
 *
 * <p>When the first provider is out of budget the request must move to the next one, not stall and
 * not fail. That is the entire reason a chain exists, and it is why the limit is checked here at
 * the resolver rather than inside each provider: one place, applied to every data type.
 */
class MarketDataResolverRateLimitTest {

    /** A provider that counts its calls and returns a fixed price. */
    private static class CountingProvider implements MarketDataProvider {
        private final String name;
        private final BigDecimal price;
        final AtomicInteger calls = new AtomicInteger();

        CountingProvider(String name, BigDecimal price) {
            this.name = name;
            this.price = price;
        }

        @Override public String getName() { return name; }
        @Override public Set<MarketDataType> supportedTypes() { return Set.of(MarketDataType.PRICE); }
        @Override public BigDecimal fetchPrice(String symbol, AssetType assetType) {
            calls.incrementAndGet();
            return price;
        }
    }

    private ProviderRateLimits config;
    private ProviderRateLimiter limiter;
    private CountingProvider first;
    private CountingProvider second;
    private MarketDataResolver resolver;

    @BeforeEach
    void setUp() {
        config = new ProviderRateLimits();
        limiter = new ProviderRateLimiter(config);
        first = new CountingProvider("first", new BigDecimal("100"));
        second = new CountingProvider("second", new BigDecimal("200"));

        budget("first", 1);
        budget("second", 50);

        resolver = new MarketDataResolver(List.of(first, second), limiter, config);
        ReflectionTestUtils.setField(resolver, "priceChain", "first,second");
        ReflectionTestUtils.setField(resolver, "mfNavChain", "");
        ReflectionTestUtils.setField(resolver, "dividendChain", "");
        ReflectionTestUtils.setField(resolver, "newsChain", "");
        resolver.init();
    }

    private void budget(String provider, int perSecond) {
        ProviderRateLimits.Limit limit = new ProviderRateLimits.Limit();
        limit.setPerSecond(perSecond);
        limit.setDocumented("test");
        limit.setSource("test");
        config.getProviders().put(provider, limit);
    }

    @Test
    @DisplayName("the first provider serves the request while it has budget")
    void firstProviderWinsNormally() {
        assertThat(resolver.getPrice("RELIANCE", AssetType.EQUITY)).isEqualByComparingTo("100");
        assertThat(first.calls).hasValue(1);
        assertThat(second.calls).as("no need to try the fallback").hasValue(0);
    }

    @Test
    @DisplayName("once the first provider is out of budget the request falls through to the next")
    void fallsThroughWhenThrottled() {
        resolver.getPrice("RELIANCE", AssetType.EQUITY);          // uses first's 1/s slot

        BigDecimal price = resolver.getPrice("RELIANCE", AssetType.EQUITY);

        assertThat(price).as("served by the fallback").isEqualByComparingTo("200");
        // Crucially the throttled provider is not called again -- the point is to stop sending it
        // requests, not to send them and have them rejected.
        assertThat(first.calls).hasValue(1);
        assertThat(second.calls).hasValue(1);
    }

    @Test
    @DisplayName("a throttled provider is skipped without waiting")
    void skippingDoesNotBlock() {
        resolver.getPrice("RELIANCE", AssetType.EQUITY);

        long start = System.currentTimeMillis();
        resolver.getPrice("RELIANCE", AssetType.EQUITY);
        long elapsed = System.currentTimeMillis() - start;

        // A user waiting on a price must not pay for another provider's exhausted quota.
        assertThat(elapsed).isLessThan(150);
    }

    @Test
    @DisplayName("when every provider is out of budget the price is unknown, not stale or wrong")
    void allThrottledYieldsNull() {
        budget("second", 1);
        resolver = new MarketDataResolver(List.of(first, second), limiter, config);
        ReflectionTestUtils.setField(resolver, "priceChain", "first,second");
        ReflectionTestUtils.setField(resolver, "mfNavChain", "");
        ReflectionTestUtils.setField(resolver, "dividendChain", "");
        ReflectionTestUtils.setField(resolver, "newsChain", "");
        resolver.init();

        resolver.getPrice("RELIANCE", AssetType.EQUITY);   // first
        resolver.getPrice("RELIANCE", AssetType.EQUITY);   // second

        assertThat(resolver.getPrice("RELIANCE", AssetType.EQUITY))
                .as("null means unknown, which callers already handle").isNull();
    }

    @Test
    @DisplayName("rate limiting applies to every data type, not only prices")
    void limitCoversAllTypes() {
        // The five chain walkers share one implementation precisely so a policy cannot be applied
        // to some data types and forgotten for others.
        budget("first", 1);
        resolver.getPrice("RELIANCE", AssetType.EQUITY);

        assertThat(resolver.getDividends("RELIANCE")).isEmpty();
        assertThat(resolver.getNews("RELIANCE", 5)).isEmpty();
        assertThat(first.calls).as("no further calls to the exhausted provider").hasValue(1);
    }

    // ── the waiting variant, for batch callers ────────────────────────────────

    /** A dividend provider that counts calls and returns whatever it was given. */
    private static class CountingDividendProvider implements MarketDataProvider {
        private final String name;
        private final List<DividendEvent> events;
        final AtomicInteger calls = new AtomicInteger();

        CountingDividendProvider(String name, List<DividendEvent> events) {
            this.name = name;
            this.events = events;
        }

        @Override public String getName() { return name; }
        @Override public Set<MarketDataType> supportedTypes() { return Set.of(MarketDataType.DIVIDEND); }
        @Override public List<DividendEvent> fetchDividends(String symbol) {
            calls.incrementAndGet();
            return events;
        }
    }

    private MarketDataResolver dividendResolver(MarketDataProvider... providers) {
        StringBuilder chain = new StringBuilder();
        for (MarketDataProvider p : providers) {
            if (!chain.isEmpty()) chain.append(",");
            chain.append(p.getName());
        }
        MarketDataResolver r = new MarketDataResolver(List.of(providers), limiter, config);
        ReflectionTestUtils.setField(r, "priceChain", "");
        ReflectionTestUtils.setField(r, "mfNavChain", "");
        ReflectionTestUtils.setField(r, "dividendChain", chain.toString());
        ReflectionTestUtils.setField(r, "newsChain", "");
        r.init();
        return r;
    }

    private static DividendEvent event(String amount) {
        return DividendEvent.builder()
                .symbol("ITC.NS").amountPerShare(new BigDecimal(amount))
                .exDate(java.time.LocalDate.of(2026, 5, 27)).dividendType("Final").source("NSE").build();
    }

    @Test
    @DisplayName("the waiting variant pauses for a slot instead of skipping the provider")
    void waitingAcquiresRatherThanSkips() {
        // This is the whole fix. The non-waiting path drained nse's 1/s bucket on the first symbol and
        // silently skipped the rest, which is how 24 holdings produced 5 HTTP calls and no dividends.
        CountingDividendProvider only = new CountingDividendProvider("solo", List.of(event("8")));
        budget("solo", 1);
        MarketDataResolver r = dividendResolver(only);

        r.getDividendsWaiting("ITC.NS", java.time.Duration.ofSeconds(5));   // takes the 1/s slot
        List<DividendEvent> second = r.getDividendsWaiting("ITC.NS", java.time.Duration.ofSeconds(5));

        // Without waiting this second call is skipped and comes back empty, as getDividends would.
        assertThat(second).hasSize(1);
        assertThat(only.calls).as("both requests actually reached the provider").hasValue(2);
    }

    @Test
    @DisplayName("an empty answer and an unreachable provider are told apart")
    void emptyIsDistinguishedFromUnreachable() {
        // The event sync depends on this difference. An empty list means "this company has announced no
        // dividend" and the symbol can be marked done forever; null means nobody answered and it must be
        // retried. Conflating them would let a provider outage masquerade as "pays nothing" and leave the
        // store permanently empty -- the same silent-success failure this work exists to remove.
        CountingDividendProvider silent = new CountingDividendProvider("silent", List.of());
        budget("silent", 1);
        MarketDataResolver r = dividendResolver(silent);

        assertThat(r.getDividendsWaiting("ITC.NS", java.time.Duration.ZERO))
                .as("a provider answered with nothing").isNotNull().isEmpty();

        // Budget now spent, and ZERO wait means no second chance: nobody answered at all.
        assertThat(r.getDividendsWaiting("ITC.NS", java.time.Duration.ZERO))
                .as("no provider reachable").isNull();
    }

    @Test
    @DisplayName("the non-waiting methods still skip, so the price sweep is unaffected")
    void existingCallersStillDoNotBlock() {
        // firstResult grew a maxWait parameter; every pre-existing caller passes ZERO. If that had been
        // changed to wait, the five-minute sweep would stall on any throttled provider.
        budget("first", 1);
        resolver.getPrice("RELIANCE", AssetType.EQUITY);

        long start = System.currentTimeMillis();
        resolver.getPrice("RELIANCE", AssetType.EQUITY);
        assertThat(System.currentTimeMillis() - start).isLessThan(150);
    }
}
