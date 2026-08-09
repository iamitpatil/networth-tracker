package com.networth.service.market.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The request budget per provider.
 *
 * <p>The case that matters most is Alpha Vantage: 25 requests <b>per day</b> on the free tier, and
 * it sits last in the price chain, so it is only reached when the two providers ahead of it have
 * failed. That is precisely when a retry loop would spend the whole day's quota in seconds, and
 * nothing would report it — the requests would just start returning null.
 */
class ProviderRateLimiterTest {

    private ProviderRateLimits config;
    private ProviderRateLimiter limiter;

    @BeforeEach
    void setUp() {
        config = new ProviderRateLimits();
        limiter = new ProviderRateLimiter(config);
    }

    /** Replaces one provider's budget so tests do not depend on the shipped defaults. */
    private void budget(String provider, Integer perSecond, Integer perMinute, Integer perDay) {
        ProviderRateLimits.Limit limit = new ProviderRateLimits.Limit();
        limit.setPerSecond(perSecond);
        limit.setPerMinute(perMinute);
        limit.setPerDay(perDay);
        limit.setDocumented("test");
        limit.setSource("test");
        config.getProviders().put(provider, limit);
    }

    // ── the budget is respected ───────────────────────────────────────

    @Test
    @DisplayName("requests are allowed up to the limit and refused after it")
    void allowsUpToTheLimit() {
        budget("test", null, 3, null);

        assertThat(limiter.tryAcquire("test")).isTrue();
        assertThat(limiter.tryAcquire("test")).isTrue();
        assertThat(limiter.tryAcquire("test")).isTrue();
        assertThat(limiter.tryAcquire("test")).as("fourth in a 3/min window").isFalse();
    }

    @Test
    @DisplayName("the tightest window wins, not the most generous")
    void tightestWindowBinds() {
        // 100/min would allow all of these, but 2/s does not.
        budget("test", 2, 100, null);

        assertThat(limiter.tryAcquire("test")).isTrue();
        assertThat(limiter.tryAcquire("test")).isTrue();
        assertThat(limiter.tryAcquire("test")).as("third within the same second").isFalse();
    }

    @Test
    @DisplayName("a per-second slot frees up once the second has passed")
    void windowRolls() throws InterruptedException {
        budget("test", 1, null, null);

        assertThat(limiter.tryAcquire("test")).isTrue();
        assertThat(limiter.tryAcquire("test")).isFalse();

        Thread.sleep(1100);

        assertThat(limiter.tryAcquire("test")).as("after the second elapsed").isTrue();
    }

    @Test
    @DisplayName("providers have independent budgets")
    void providersAreIndependent() {
        budget("a", 1, null, null);
        budget("b", 1, null, null);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        // Exhausting one must not affect the other, or a failing provider would take the chain
        // down with it.
        assertThat(limiter.tryAcquire("b")).isTrue();
    }

    // ── waiting vs skipping ───────────────────────────────────────────

    @Test
    @DisplayName("tryAcquire does not wait; it reports refusal immediately")
    void tryAcquireReturnsAtOnce() {
        budget("test", 1, null, null);
        limiter.tryAcquire("test");

        long start = System.currentTimeMillis();
        boolean allowed = limiter.tryAcquire("test");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(allowed).isFalse();
        // The live read path must fall through to the next provider, not block an HTTP thread.
        assertThat(elapsed).isLessThan(100);
    }

    @Test
    @DisplayName("acquire waits for a slot when one is coming")
    void acquireWaitsForASlot() {
        budget("test", 1, null, null);
        config.setMaxWait(Duration.ofSeconds(2));
        limiter.tryAcquire("test");

        long start = System.currentTimeMillis();
        boolean allowed = limiter.acquire("test");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(allowed).as("a per-second slot frees within the wait budget").isTrue();
        assertThat(elapsed).isGreaterThan(500);
    }

    @Test
    @DisplayName("acquire gives up rather than waiting for a slot a day away")
    void acquireGivesUpOnADailyLimit() {
        budget("test", null, null, 1);
        config.setMaxWait(Duration.ofMillis(200));
        limiter.tryAcquire("test");

        long start = System.currentTimeMillis();
        boolean allowed = limiter.acquire("test");

        assertThat(allowed).isFalse();
        // Must not sit waiting 24 hours for the daily window to roll.
        assertThat(System.currentTimeMillis() - start).isLessThan(1000);
    }

    // ── escape hatches ────────────────────────────────────────────────

    @Test
    @DisplayName("disabling the limiter allows everything, matching the previous behaviour")
    void disabledAllowsEverything() {
        budget("test", 1, null, null);
        config.setEnabled(false);

        for (int i = 0; i < 50; i++) {
            assertThat(limiter.tryAcquire("test")).isTrue();
        }
    }

    @Test
    @DisplayName("an unconfigured provider is allowed rather than blocked")
    void unknownProviderIsAllowed() {
        // A provider added to a chain without a limit entry is a config gap. Refusing all of its
        // requests would look like the provider being down.
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryAcquire("brand-new-provider")).isTrue();
        }
    }

    // ── the shipped figures ───────────────────────────────────────────

    @Test
    @DisplayName("Alpha Vantage is throttled hard: 1/s, 5/min and 25 a day")
    void alphaVantageIsThrottledHard() {
        // The real shipped defaults, not a test budget. Three windows apply and the tightest wins,
        // so a retry loop cannot spend the daily quota in a burst -- which is the failure mode,
        // because the requests would simply start returning null with nothing to explain why.
        assertThat(limiter.tryAcquire("alpha-vantage")).isTrue();
        assertThat(limiter.tryAcquire("alpha-vantage"))
                .as("second within the same second, against a 1/s budget").isFalse();

        ProviderRateLimits.Limit limit = config.forProvider("alpha-vantage");
        assertThat(limit.getPerSecond()).isEqualTo(1);
        assertThat(limit.getPerMinute()).isEqualTo(5);
        assertThat(limit.getPerDay()).isEqualTo(25);
        assertThat(limit.getDocumented()).contains("25 requests/day");
        // 25 a day is roughly one request every 58 minutes, so the daily cap is the real
        // constraint even though a shorter window is what refuses any given burst.
        assertThat(limit.windows()).extracting(ProviderRateLimits.Window::label)
                .contains("1/s", "5/min", "25/day");
    }

    @Test
    @DisplayName("every shipped provider records both its documented limit and a source")
    void everyProviderIsDocumented() {
        assertThat(config.getProviders()).isNotEmpty();
        config.getProviders().forEach((name, limit) -> {
            assertThat(limit.getDocumented()).as("documented limit for %s", name).isNotBlank();
            assertThat(limit.getSource()).as("source for %s", name).isNotBlank();
            assertThat(limit.windows()).as("at least one enforced window for %s", name).isNotEmpty();
        });
    }

    @Test
    @DisplayName("Upstox's 30-minute ceiling is enforced, not just its per-minute burst")
    void upstoxHalfHourCeilingIsEnforced() {
        // 400/min sustained would be 12,000 per half hour against a 2,000 cap, so the longer
        // window has to be tracked or a backfill would sail past the real limit.
        ProviderRateLimits.Limit limit = config.forProvider("upstox");
        assertThat(limit.getPer30Minutes()).isEqualTo(2000);
        assertThat(limit.windows()).extracting(ProviderRateLimits.Window::label)
                .contains("2000/30min");
    }

    // ── observability ─────────────────────────────────────────────────

    @Test
    @DisplayName("the snapshot reports usage, remaining budget and refusals")
    void snapshotReportsUsage() {
        budget("test", null, 2, null);
        limiter.tryAcquire("test");
        limiter.tryAcquire("test");
        limiter.tryAcquire("test");   // refused

        Map<String, Object> row = limiter.snapshot().stream()
                .filter(r -> "test".equals(r.get("provider")))
                .findFirst().orElseThrow();

        assertThat(row.get("atLimit")).isEqualTo(true);
        assertThat(row.get("throttledRequests")).isEqualTo(1L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> windows = (List<Map<String, Object>>) row.get("enforced");
        assertThat(windows).hasSize(1);
        assertThat(windows.get(0).get("used")).isEqualTo(2L);
        assertThat(windows.get(0).get("remaining")).isEqualTo(0L);
    }

    @Test
    @DisplayName("an untouched provider reports zero usage rather than failing")
    void snapshotHandlesUnusedProviders() {
        List<Map<String, Object>> snapshot = limiter.snapshot();

        assertThat(snapshot).isNotEmpty();
        assertThat(snapshot).allSatisfy(row -> assertThat(row.get("atLimit")).isEqualTo(false));
    }
}
