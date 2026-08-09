package com.networth.service.market;

import com.networth.model.enums.AssetType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * Whether a stored price is old enough that re-fetching it could produce a different number.
 *
 * <p>Every path that refreshes a price asks this one question — the 15-minute sweep, the read path
 * behind the Holdings page, and the "as of" badge shown to the user. Before this existed each path
 * decided for itself, which is how a six-month-old row came to be served as today's price while the
 * scheduler simultaneously re-fetched everything it already had.
 *
 * <h2>Age alone is the wrong question</h2>
 * A quote is not stale because time passed; it is stale because a <em>newer</em> quote exists. Those
 * differ, and the difference is the whole point of this class:
 *
 * <ul>
 *   <li>An equity price confirmed at 15:29 IST is still the best number available at 03:00, because
 *       the exchange shut at 15:30 and has not reopened. Fourteen hours old, and perfectly current.
 *   <li>An equity price confirmed at 09:10 IST is stale six minutes later, because the market opened
 *       at 09:15 and the pre-open number has been superseded.
 *   <li>A NAV fetched at noon is yesterday's NAV whatever its age says, because AMFI publishes the
 *       new one around 23:00. A pure age check would have the 23:30 job skip the very publication it
 *       exists to collect, since noon is only eleven hours ago.
 * </ul>
 *
 * <p>So each asset type is classified by how its prices come into existence, and the age in
 * {@code market.price.max-age.*} only decides how finely to sample <em>within</em> a live session.
 *
 * <h2>The sweep must tick faster than the shortest max age</h2>
 * A threshold equal to the poll period never fires: at each tick the age is the period minus however
 * long the previous fetch took, which is always just under. The equity max age is 15 minutes and the
 * sweep runs every 5, so a symbol refreshes on the first tick past 15 minutes. Raising the max age
 * above the tick period is safe; lowering the tick period below it is what matters.
 *
 * @see MarketCalendar for why the server pins a zone at all
 */
@Component
@ConfigurationProperties(prefix = "market.price")
@Slf4j
public class PriceFreshnessPolicy {

    /**
     * How a type's prices come into existence, which is what decides when a re-fetch can help.
     */
    private enum Cadence {
        /** Priced continuously, but only while the exchange is open. */
        MARKET_HOURS,
        /** Priced continuously, no session to wait for. */
        CONTINUOUS,
        /** One number per business day, published at a known hour. */
        DAILY,
        /** Not market priced. Its value comes from a statement, a contribution or a valuation. */
        NEVER,
    }

    private static final Map<AssetType, Cadence> CADENCE = cadences();

    /** Used for a type not named in {@link #CADENCE}; also the fallback for an unmapped max age. */
    private static final Duration DEFAULT_MAX_AGE = Duration.ofMinutes(15);

    private final MarketCalendar calendar;

    /**
     * How finely to sample a continuously-priced instrument. Override per type with
     * {@code market.price.max-age.equity=5m}, {@code market.price.max-age.gold=2h} and so on; types
     * left out keep the defaults below, so tuning one cannot silently unbound the rest.
     *
     * <p>Only consulted for {@link Cadence#MARKET_HOURS} and {@link Cadence#CONTINUOUS} types.
     * {@code MUTUAL_FUND}, {@code NPS} and {@code BOND} are governed by {@link #navPublishedAt}
     * instead, and the unpriceable types by nothing at all.
     */
    @Getter
    @Setter
    private Map<AssetType, Duration> maxAge = defaultMaxAges();

    /**
     * When the day's NAVs become available, in the market's zone. AMFI publishes around 23:00 IST;
     * the scheduled NAV job deliberately runs after it.
     */
    @Getter
    @Setter
    private LocalTime navPublishedAt = LocalTime.of(23, 0);

    public PriceFreshnessPolicy(MarketCalendar calendar) {
        this.calendar = calendar;
    }

    /**
     * Whether there is a market price to fetch for this type at all.
     *
     * <p>An EPF balance changes when an employer files a return and a house is worth what someone
     * says it is worth; no provider can be asked. Callers skip these rather than fetching and
     * discarding, so a portfolio of fixed deposits makes no outbound requests.
     */
    public boolean isPriceable(AssetType assetType) {
        return cadence(assetType) != Cadence.NEVER;
    }

    /** {@link #isStale(AssetType, Instant, Instant)} against the current instant. */
    public boolean isStale(AssetType assetType, Instant lastConfirmedAt) {
        return isStale(assetType, lastConfirmedAt, Instant.now());
    }

    /**
     * Whether a price last confirmed at {@code lastConfirmedAt} is worth re-fetching at {@code now}.
     *
     * @param lastConfirmedAt when a provider last returned this price, not when the row was created;
     *                        {@code null} means never, which is always stale
     * @return {@code false} for an unpriceable type — there is nothing to fetch, so nothing can be
     *         out of date
     */
    public boolean isStale(AssetType assetType, Instant lastConfirmedAt, Instant now) {
        Cadence cadence = cadence(assetType);
        if (cadence == Cadence.NEVER) {
            return false;
        }
        if (lastConfirmedAt == null) {
            return true;
        }
        return switch (cadence) {
            case MARKET_HOURS -> supersededBySession(assetType, lastConfirmedAt, now);
            case CONTINUOUS -> olderThanMaxAge(assetType, lastConfirmedAt, now);
            case DAILY -> lastConfirmedAt.isBefore(lastPublication(now));
            case NEVER -> false;
        };
    }

    /** The sampling interval for a continuously-priced type. Public so callers can explain a badge. */
    public Duration maxAgeFor(AssetType assetType) {
        return maxAge.getOrDefault(assetType, DEFAULT_MAX_AGE);
    }

    /**
     * How much longer a price confirmed at {@code lastConfirmedAt} may be served before this policy
     * would call it stale. Zero if it already is.
     *
     * <p>Exists so a write-through cache can expire an entry exactly when the policy stops vouching
     * for it, instead of on a TTL that merely resembles the max age. The resemblance was not enough:
     * a NAV was cached for 24 hours against a publication that happens every 23, so between 23:00 and
     * the following midnight Redis served a number this policy considered superseded — and the badge,
     * reading the row rather than the cache, would have called the same figure stale on screen.
     *
     * <p>Under-estimating is harmless: the entry expires early, the next reader reads the row, finds
     * it fresh and re-caches. Over-estimating hands out a stale price, so the closed-market branch
     * measures to the reopen rather than adding a max age the session will not honour.
     */
    public Duration remainingFreshness(AssetType assetType, Instant lastConfirmedAt, Instant now) {
        if (lastConfirmedAt == null || isStale(assetType, lastConfirmedAt, now)) {
            return Duration.ZERO;
        }
        Duration remaining = switch (cadence(assetType)) {
            case MARKET_HOURS -> calendar.isMarketOpen(now)
                    ? maxAgeFor(assetType).minus(Duration.between(lastConfirmedAt, now))
                    : Duration.between(now, calendar.nextOpen(now));
            case CONTINUOUS -> maxAgeFor(assetType).minus(Duration.between(lastConfirmedAt, now));
            case DAILY -> Duration.between(now, nextPublication(lastConfirmedAt));
            // Nothing can supersede it, but an unbounded entry would outlive a corrected statement.
            case NEVER -> maxAgeFor(assetType);
        };
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * A closing price is current until the exchange reopens; a price from within the running session
     * is current for {@link #maxAgeFor}.
     *
     * <p>Asking whether a session has <em>begun</em> since the price was confirmed covers both the
     * weekend (none has, so Friday's close stands through Sunday) and the pre-open quote (one has,
     * so 09:10 is stale at 09:16 despite being six minutes old).
     */
    private boolean supersededBySession(AssetType assetType, Instant lastConfirmedAt, Instant now) {
        if (!calendar.nextOpen(lastConfirmedAt).isAfter(now)) {
            return true;
        }
        return calendar.isMarketOpen(now) && olderThanMaxAge(assetType, lastConfirmedAt, now);
    }

    private boolean olderThanMaxAge(AssetType assetType, Instant lastConfirmedAt, Instant now) {
        return Duration.between(lastConfirmedAt, now).compareTo(maxAgeFor(assetType)) > 0;
    }

    /**
     * The most recent instant at which a new daily figure became available, at or before {@code now}.
     *
     * <p>Business days only: nothing is published on a Saturday, so a NAV confirmed after Friday's
     * publication stays fresh all weekend instead of prompting two pointless bulk downloads.
     */
    private Instant lastPublication(Instant now) {
        ZonedDateTime local = now.atZone(calendar.zone());
        LocalDate date = local.toLocalTime().isBefore(navPublishedAt)
                ? local.toLocalDate().minusDays(1)
                : local.toLocalDate();
        while (!calendar.isTradingDay(date)) {
            date = date.minusDays(1);
        }
        return date.atTime(navPublishedAt).atZone(calendar.zone()).toInstant();
    }

    /**
     * The first instant strictly after {@code from} at which a new daily figure becomes available.
     *
     * <p>The mirror of {@link #lastPublication}, and the two must agree: whatever this returns is the
     * moment a price confirmed at {@code from} turns stale, so a cache expiring then expires neither
     * early nor late.
     */
    private Instant nextPublication(Instant from) {
        ZonedDateTime local = from.atZone(calendar.zone());
        LocalDate date = local.toLocalTime().isBefore(navPublishedAt)
                ? local.toLocalDate()
                : local.toLocalDate().plusDays(1);
        while (!calendar.isTradingDay(date)) {
            date = date.plusDays(1);
        }
        return date.atTime(navPublishedAt).atZone(calendar.zone()).toInstant();
    }

    private Cadence cadence(AssetType assetType) {
        // A type added to the enum without a provider behind it should not generate requests.
        return assetType == null ? Cadence.NEVER : CADENCE.getOrDefault(assetType, Cadence.NEVER);
    }

    private static Map<AssetType, Cadence> cadences() {
        Map<AssetType, Cadence> map = new EnumMap<>(AssetType.class);
        map.put(AssetType.EQUITY, Cadence.MARKET_HOURS);
        map.put(AssetType.ETF, Cadence.MARKET_HOURS);

        map.put(AssetType.CRYPTO, Cadence.CONTINUOUS);
        // Priced from the gold rate per gram rather than the exchange quote, so no session applies.
        map.put(AssetType.GOLD, Cadence.CONTINUOUS);
        map.put(AssetType.SGB, Cadence.CONTINUOUS);

        map.put(AssetType.MUTUAL_FUND, Cadence.DAILY);
        map.put(AssetType.NPS, Cadence.DAILY);
        // Best effort: no bond provider is wired yet, so a refresh is usually a no-op. Classified
        // here anyway so wiring one needs no change to this policy.
        map.put(AssetType.BOND, Cadence.DAILY);

        map.put(AssetType.EPF, Cadence.NEVER);
        map.put(AssetType.PPF, Cadence.NEVER);
        map.put(AssetType.FD, Cadence.NEVER);
        map.put(AssetType.CASH, Cadence.NEVER);
        map.put(AssetType.REAL_ESTATE, Cadence.NEVER);
        return map;
    }

    private static Map<AssetType, Duration> defaultMaxAges() {
        Map<AssetType, Duration> map = new EnumMap<>(AssetType.class);
        // Also the Redis TTL for these types, via remainingFreshness -- one number, not two that agree.
        map.put(AssetType.EQUITY, Duration.ofMinutes(15));
        map.put(AssetType.ETF, Duration.ofMinutes(15));
        map.put(AssetType.CRYPTO, Duration.ofMinutes(15));
        // Retail gold rates move slowly and the source publishes a daily-ish figure; hourly polling
        // would spend requests to re-read the same number.
        map.put(AssetType.GOLD, Duration.ofHours(6));
        map.put(AssetType.SGB, Duration.ofHours(6));
        return map;
    }
}
