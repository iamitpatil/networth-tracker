package com.networth.service.market;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * The Indian market and tax calendar: what "today" means, and whether the exchange is open.
 *
 * <h2>Why the server needs a time zone at all</h2>
 * Instants are stored in UTC and rendered in the viewer's zone by the browser, which is correct and
 * needs nothing from this class. Business dates are stored without a zone, which also needs nothing
 * from this class. What does need a zone is the third category: decisions the server makes with no
 * browser present, about events that happen on Indian time.
 *
 * <ul>
 *   <li><b>The financial year boundary is a legal fact in IST.</b> At 20:00 EDT on 31 March it is
 *       already 1 April in India and the year has rolled over for tax purposes, wherever the user
 *       happens to be sitting. Deriving the current financial year from a browser would give a user
 *       abroad the wrong tax year for several hours a day.</li>
 *   <li><b>Schedules chase Indian events.</b> AMFI publishes NAVs around 23:00 IST; the NSE trades
 *       09:15 to 15:30 IST. A cron has no user and no browser to ask.</li>
 *   <li><b>Freshness depends on whether the market is open.</b> Re-fetching an equity quote at 03:00
 *       IST cannot produce a new number, because the exchange is shut.</li>
 * </ul>
 *
 * <p>This is deliberately not a user preference. It is the market and tax jurisdiction the
 * deployment serves, which is why it is expressed as a calendar rather than as a setting sprayed
 * across the code, and why nothing here mutates the JVM's default zone. A process-wide default is
 * invisible at the call site, affects every library in the process, and makes correctness depend on
 * bean initialisation order; an explicit calendar does not.
 */
@Component
@Slf4j
public class MarketCalendar {

    /**
     * The jurisdiction's zone, also exposed statically for the handful of places where injecting a
     * bean is impractical. One definition either way.
     */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    /** NSE and BSE continuous trading session. */
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    private final ZoneId zone;

    public MarketCalendar(@Value("${app.market-timezone:Asia/Kolkata}") String timezone) {
        ZoneId configured;
        try {
            configured = ZoneId.of(timezone);
        } catch (RuntimeException e) {
            // A typo would silently reintroduce the wrong-financial-year bug this class exists to
            // prevent, so fail the boot rather than fall back to the host's zone.
            throw new IllegalStateException("app.market-timezone '" + timezone
                    + "' is not a valid zone ID (expected something like Asia/Kolkata)", e);
        }
        this.zone = configured;
        log.info("Market calendar: {} (financial years, exchange hours and schedules follow this "
                + "zone; stored instants remain UTC)", configured);
    }

    public ZoneId zone() {
        return zone;
    }

    /** Today's date in the market's zone, which is what a financial year is measured against. */
    public LocalDate today() {
        return LocalDate.now(zone);
    }

    /** The current local date and time in the market's zone. */
    public LocalDateTime now() {
        return LocalDateTime.now(zone);
    }

    /**
     * Whether the exchange trades on a date.
     *
     * <p>Weekends only. Trading holidays are published annually by the exchanges and are not
     * modelled here; the effect of missing them is a refresh attempt that returns the previous
     * close, which is harmless. Reporting a holiday as a trading day would only ever cost a
     * redundant request.
     */
    public boolean isTradingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /** Whether the exchange is open right now. */
    public boolean isMarketOpen() {
        return isMarketOpen(Instant.now());
    }

    public boolean isMarketOpen(Instant at) {
        ZonedDateTime local = at.atZone(zone);
        if (!isTradingDay(local.toLocalDate())) {
            return false;
        }
        LocalTime time = local.toLocalTime();
        return !time.isBefore(MARKET_OPEN) && !time.isAfter(MARKET_CLOSE);
    }

    /**
     * When the exchange next opens, at or after the given instant.
     *
     * <p>Used to decide how long a closing price stays fresh: there is no point re-fetching an
     * equity quote until the market reopens, because no new price can exist before then.
     */
    public Instant nextOpen(Instant from) {
        ZonedDateTime local = from.atZone(zone);
        ZonedDateTime candidate = local.with(MARKET_OPEN);
        if (!candidate.isAfter(local)) {
            candidate = candidate.plusDays(1);
        }
        while (!isTradingDay(candidate.toLocalDate())) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }

    /** How long until the exchange next opens. Zero while it is open. */
    public Duration untilNextOpen(Instant from) {
        return isMarketOpen(from) ? Duration.ZERO : Duration.between(from, nextOpen(from));
    }
}
