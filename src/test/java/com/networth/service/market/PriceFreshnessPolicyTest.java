package com.networth.service.market;

import com.networth.model.enums.AssetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The freshness policy, tested at its boundaries — which is where it would be wrong.
 *
 * <p>The two bugs behind this class were both freshness failures in opposite directions: a
 * six-month-old row served as today's price, and a scheduler that re-fetched every symbol every 15
 * minutes including at 03:00 when the exchange was shut. So each case below pins down one of the two
 * questions: is a re-fetch <em>needed</em>, and is a re-fetch <em>useless</em>.
 *
 * <p>Nothing here uses {@code Instant.now()}: every assertion names an IST wall-clock time, so the
 * results hold whatever zone the host runs in.
 */
class PriceFreshnessPolicyTest {

    private final PriceFreshnessPolicy policy = new PriceFreshnessPolicy(new MarketCalendar("Asia/Kolkata"));

    /** The instant at a given IST wall-clock time, independent of the host's zone. */
    private static Instant ist(int y, int m, int d, int hh, int mm) {
        return LocalDateTime.of(y, m, d, hh, mm).atZone(MarketCalendar.ZONE).toInstant();
    }

    // August 2026: the 7th is a Friday, the 8th a Saturday, the 9th a Sunday, the 10th a Monday.

    // ── what can be priced at all ─────────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = AssetType.class, names = {"EPF", "PPF", "FD", "CASH", "REAL_ESTATE"})
    @DisplayName("unpriced types are never priceable and so never stale")
    void unpricedTypesAreNeverStale(AssetType type) {
        assertThat(policy.isPriceable(type)).isFalse();

        // Not "fresh" in any meaningful sense — there is simply nothing to fetch, and a caller that
        // treated these as stale would make a provider call per fixed deposit, forever.
        assertThat(policy.isStale(type, null, ist(2026, 8, 10, 12, 0))).isFalse();
        assertThat(policy.isStale(type, ist(2020, 1, 1, 12, 0), ist(2026, 8, 10, 12, 0))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = AssetType.class,
            names = {"EQUITY", "ETF", "MUTUAL_FUND", "NPS", "GOLD", "SGB", "CRYPTO", "BOND"})
    @DisplayName("market-priced types are priceable, and a price never confirmed is always stale")
    void neverConfirmedIsStale(AssetType type) {
        assertThat(policy.isPriceable(type)).isTrue();
        assertThat(policy.isStale(type, null, ist(2026, 8, 10, 12, 0))).isTrue();
    }

    // ── equities and ETFs: bounded by the session ──────────────────────

    @Test
    @DisplayName("inside the session, a quote is good for the max age")
    void intradaySampling() {
        Instant confirmed = ist(2026, 8, 10, 11, 0);

        assertThat(policy.isStale(AssetType.EQUITY, confirmed, ist(2026, 8, 10, 11, 14)))
                .as("14 minutes old").isFalse();
        assertThat(policy.isStale(AssetType.EQUITY, confirmed, ist(2026, 8, 10, 11, 15)))
                .as("exactly at the max age").isFalse();
        assertThat(policy.isStale(AssetType.EQUITY, confirmed, ist(2026, 8, 10, 11, 16)))
                .as("16 minutes old").isTrue();
    }

    @Test
    @DisplayName("a pre-open quote is stale the moment the market opens, however young it is")
    void openingSupersedesThePreOpenQuote() {
        // Six minutes old and already wrong: 09:10 is pre-open, so it cannot be a traded price. Age
        // alone would call this fresh, which is the mistake this class exists to avoid.
        Instant preOpen = ist(2026, 8, 10, 9, 10);

        assertThat(policy.isStale(AssetType.EQUITY, preOpen, ist(2026, 8, 10, 9, 14)))
                .as("09:14, market still shut").isFalse();
        assertThat(policy.isStale(AssetType.EQUITY, preOpen, ist(2026, 8, 10, 9, 16)))
                .as("09:16, session running").isTrue();
    }

    @Test
    @DisplayName("the closing price stands after 15:30, however long the evening lasts")
    void theCloseIsHeldUntilTheNextOpen() {
        Instant nearClose = ist(2026, 8, 10, 15, 25);

        assertThat(policy.isStale(AssetType.EQUITY, ist(2026, 8, 10, 15, 10), ist(2026, 8, 10, 15, 29)))
                .as("15:29, still trading and 19 minutes old").isTrue();
        assertThat(policy.isStale(AssetType.EQUITY, nearClose, ist(2026, 8, 10, 15, 31)))
                .as("15:31, one minute past the close").isFalse();
        assertThat(policy.isStale(AssetType.EQUITY, nearClose, ist(2026, 8, 11, 3, 0)))
                .as("03:00, when the old UTC-scheduled job used to fire").isFalse();
        assertThat(policy.isStale(AssetType.EQUITY, nearClose, ist(2026, 8, 11, 9, 14)))
                .as("09:14 next morning, not open yet").isFalse();
        assertThat(policy.isStale(AssetType.EQUITY, nearClose, ist(2026, 8, 11, 9, 20)))
                .as("09:20 next morning, session running").isTrue();
    }

    @Test
    @DisplayName("Friday's close is fresh all weekend and stale on Monday morning")
    void theWeekendIsNotStaleness() {
        Instant fridayEvening = ist(2026, 8, 7, 16, 0);

        assertThat(policy.isStale(AssetType.EQUITY, fridayEvening, ist(2026, 8, 8, 12, 0)))
                .as("Saturday midday").isFalse();
        assertThat(policy.isStale(AssetType.ETF, fridayEvening, ist(2026, 8, 9, 20, 0)))
                .as("Sunday evening, ETFs follow the same session").isFalse();
        assertThat(policy.isStale(AssetType.EQUITY, fridayEvening, ist(2026, 8, 10, 9, 20)))
                .as("Monday 09:20").isTrue();
    }

    @Test
    @DisplayName("a session missed entirely is stale even at the weekend")
    void aMissedSessionIsStale() {
        // Thursday's price, checked on Saturday: Friday traded and we have nothing from it. Holding
        // the "close" here would show a two-day-old figure as current until Monday.
        assertThat(policy.isStale(AssetType.EQUITY, ist(2026, 8, 6, 15, 20), ist(2026, 8, 8, 12, 0)))
                .isTrue();
    }

    // ── round-the-clock instruments ───────────────────────────────────

    @Test
    @DisplayName("crypto ignores the exchange calendar entirely")
    void cryptoHasNoSession() {
        Instant sundayNight = ist(2026, 8, 9, 2, 0);

        assertThat(policy.isStale(AssetType.CRYPTO, sundayNight, ist(2026, 8, 9, 2, 14)))
                .as("14 minutes old on a Sunday").isFalse();
        assertThat(policy.isStale(AssetType.CRYPTO, sundayNight, ist(2026, 8, 9, 2, 30)))
                .as("half an hour old on a Sunday").isTrue();
    }

    @Test
    @DisplayName("gold and SGBs are sampled every six hours")
    void goldIsSampledSlowly() {
        Instant morning = ist(2026, 8, 10, 8, 0);

        assertThat(policy.maxAgeFor(AssetType.GOLD)).isEqualTo(Duration.ofHours(6));
        assertThat(policy.isStale(AssetType.GOLD, morning, ist(2026, 8, 10, 13, 0)))
                .as("five hours old").isFalse();
        assertThat(policy.isStale(AssetType.SGB, morning, ist(2026, 8, 10, 15, 0)))
                .as("seven hours old; SGBs are priced off the gold rate, not the quote").isTrue();
    }

    // ── once-a-day publications ───────────────────────────────────────

    @Test
    @DisplayName("a NAV read during the day does not satisfy the evening's publication")
    void aDaytimeNavDoesNotSatisfyTheEveningJob() {
        // The trap a pure age check falls into: at 23:30 this row is eleven and a half hours old, so
        // any "once per day" age threshold calls it fresh — and the 23:30 job skips the publication
        // it exists to collect. What matters is that 23:00 has passed since it was confirmed.
        Instant noon = ist(2026, 8, 10, 12, 0);

        assertThat(Duration.between(noon, ist(2026, 8, 10, 23, 30))).isLessThan(Duration.ofHours(24));
        assertThat(policy.isStale(AssetType.MUTUAL_FUND, noon, ist(2026, 8, 10, 23, 30))).isTrue();
        assertThat(policy.isStale(AssetType.NPS, noon, ist(2026, 8, 10, 23, 30))).isTrue();
    }

    @Test
    @DisplayName("once the day's NAV is in hand, nothing more is published until tomorrow")
    void todaysNavSatisfiesTheDay() {
        Instant afterPublication = ist(2026, 8, 10, 23, 10);

        assertThat(policy.isStale(AssetType.MUTUAL_FUND, afterPublication, ist(2026, 8, 10, 23, 30)))
                .as("twenty minutes later, same publication").isFalse();
        assertThat(policy.isStale(AssetType.MUTUAL_FUND, afterPublication, ist(2026, 8, 11, 12, 0)))
                .as("next midday, tonight's NAV not out yet").isFalse();
        assertThat(policy.isStale(AssetType.MUTUAL_FUND, afterPublication, ist(2026, 8, 11, 23, 5)))
                .as("next evening, a new NAV has been published").isTrue();
    }

    @Test
    @DisplayName("no NAV is published at the weekend, so Friday's stands until Monday night")
    void navsAreNotPublishedAtWeekends() {
        Instant fridayNight = ist(2026, 8, 7, 23, 30);

        assertThat(policy.isStale(AssetType.MUTUAL_FUND, fridayNight, ist(2026, 8, 8, 23, 30)))
                .as("Saturday night").isFalse();
        assertThat(policy.isStale(AssetType.MUTUAL_FUND, fridayNight, ist(2026, 8, 9, 23, 30)))
                .as("Sunday night").isFalse();
        assertThat(policy.isStale(AssetType.MUTUAL_FUND, fridayNight, ist(2026, 8, 10, 23, 30)))
                .as("Monday night").isTrue();
    }

    @Test
    @DisplayName("bonds are treated as a daily figure, best effort")
    void bondsAreDaily() {
        assertThat(policy.isStale(AssetType.BOND, ist(2026, 8, 10, 12, 0), ist(2026, 8, 10, 23, 30)))
                .isTrue();
        assertThat(policy.isStale(AssetType.BOND, ist(2026, 8, 10, 23, 10), ist(2026, 8, 11, 12, 0)))
                .isFalse();
    }

    // ── configuration ─────────────────────────────────────────────────

    @Test
    @DisplayName("a max age override applies to that type and leaves the others alone")
    void overridingOneTypeDoesNotUnboundTheRest() {
        policy.getMaxAge().put(AssetType.EQUITY, Duration.ofMinutes(1));

        assertThat(policy.isStale(AssetType.EQUITY, ist(2026, 8, 10, 11, 0), ist(2026, 8, 10, 11, 2)))
                .as("two minutes, against a one-minute override").isTrue();
        assertThat(policy.isStale(AssetType.ETF, ist(2026, 8, 10, 11, 0), ist(2026, 8, 10, 11, 2)))
                .as("ETFs keep the default").isFalse();
    }

    @Test
    @DisplayName("a clock that runs backwards does not make a price stale")
    void aFutureTimestampIsNotStale() {
        // Container and database clocks can disagree by seconds. Reading that as staleness would
        // re-fetch every price on every pass.
        assertThat(policy.isStale(AssetType.EQUITY, ist(2026, 8, 10, 11, 5), ist(2026, 8, 10, 11, 0)))
                .isFalse();
    }

    // ── remaining freshness, which is what the Redis TTL is set from ──

    @Test
    @DisplayName("a live quote is cacheable for the rest of its max age, not a fresh full window")
    void remainingFreshnessShrinksWithAge() {
        assertThat(policy.remainingFreshness(AssetType.EQUITY, ist(2026, 8, 10, 11, 0), ist(2026, 8, 10, 11, 10)))
                .as("15-minute window, 10 minutes gone")
                .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("after the close, a price is cacheable until the market reopens")
    void remainingFreshnessRunsToTheReopen() {
        // The point of not simply reusing the max age: the closing price does not change overnight, so
        // expiring the entry every 15 minutes would send every evening reader to the database for a
        // number that cannot have moved.
        assertThat(policy.remainingFreshness(AssetType.EQUITY, ist(2026, 8, 10, 15, 29), ist(2026, 8, 10, 20, 0)))
                .as("Monday 20:00 to Tuesday's 09:15 open")
                .isEqualTo(Duration.ofHours(13).plusMinutes(15));
    }

    @Test
    @DisplayName("a NAV is cacheable until the next publication, not for a flat 24 hours")
    void remainingFreshnessRunsToTheNextPublication() {
        // The bug the old fixed TTL had: 24 hours against a 23:00 publication meant an entry cached at
        // 22:00 outlived the NAV it held by an hour, and served it while the badge called it stale.
        assertThat(policy.remainingFreshness(AssetType.MUTUAL_FUND, ist(2026, 8, 10, 23, 10), ist(2026, 8, 11, 12, 0)))
                .as("Tuesday noon to Tuesday 23:00")
                .isEqualTo(Duration.ofHours(11));
    }

    @Test
    @DisplayName("nothing is cacheable once the policy calls it stale")
    void anAlreadyStalePriceHasNoRemainingFreshness() {
        assertThat(policy.remainingFreshness(AssetType.EQUITY, ist(2026, 8, 10, 11, 0), ist(2026, 8, 10, 11, 40)))
                .isEqualTo(Duration.ZERO);
        assertThat(policy.remainingFreshness(AssetType.EQUITY, null, ist(2026, 8, 10, 11, 0)))
                .isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("an unpriceable type is cacheable but not forever")
    void anUnpriceableTypeStillExpires() {
        // It cannot go stale, so any TTL is safe; a bounded one means a corrected EPF balance appears
        // without waiting for Redis to be restarted.
        assertThat(policy.remainingFreshness(AssetType.EPF, ist(2026, 1, 1, 10, 0), ist(2026, 8, 10, 11, 0)))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("remaining freshness never outlasts staleness, hour by hour, for every type")
    void theTwoAnswersNeverContradictEachOther() {
        // The property that matters: if a TTL ever exceeded the moment isStale flips, Redis would serve
        // a price the rest of the system calls stale. Checked across a week so the weekend, the
        // publication hour and the session boundaries are all covered.
        Instant confirmedAt = ist(2026, 8, 10, 11, 0);
        for (AssetType type : AssetType.values()) {
            for (int hour = 0; hour < 24 * 7; hour++) {
                Instant now = confirmedAt.plus(Duration.ofHours(hour));
                Duration remaining = policy.remainingFreshness(type, confirmedAt, now);
                if (remaining.isZero()) {
                    continue;
                }
                assertThat(policy.isStale(type, confirmedAt, now.plus(remaining).minusSeconds(1)))
                        .as("%s cached at %s, TTL %s, must still be fresh just before it expires",
                                type, now, remaining)
                        .isFalse();
            }
        }
    }
}
