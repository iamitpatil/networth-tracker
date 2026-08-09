package com.networth.service.market;

import com.networth.service.tax.rules.TaxRuleRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The market and tax calendar.
 *
 * <p>The bug this replaces was invisible: the container had no TZ, so the JVM ran in UTC and every
 * {@code LocalDate.now()} returned the previous day between midnight and 05:30 IST. Nothing threw —
 * the wrong financial year was simply reported for five and a half hours a day. Nothing here relies
 * on the JVM's default zone, so these tests hold whatever the host is set to.
 */
class MarketCalendarTest {

    private final MarketCalendar calendar = new MarketCalendar("Asia/Kolkata");

    /** The instant at a given IST wall-clock time, independent of the host's zone. */
    private static Instant ist(int y, int m, int d, int hh, int mm) {
        return LocalDateTime.of(y, m, d, hh, mm).atZone(MarketCalendar.ZONE).toInstant();
    }

    @Test
    @DisplayName("a bad zone fails the boot rather than falling back to the host's")
    void rejectsAnInvalidZone() {
        assertThatThrownBy(() -> new MarketCalendar("Asia/Kolkatta"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid zone ID");
    }

    @Test
    @DisplayName("today is the Indian date even when the host clock says otherwise")
    void todayIsTheIndianDate() {
        // 01:00 IST on 1 April 2027 is still 31 March in UTC. The financial year has rolled over in
        // India regardless of where the server or the viewer sits, because the FY boundary is a
        // legal fact in IST.
        Instant earlyMorning = ist(2027, 4, 1, 1, 0);

        LocalDate indian = earlyMorning.atZone(MarketCalendar.ZONE).toLocalDate();
        LocalDate utc = earlyMorning.atZone(ZoneId.of("UTC")).toLocalDate();

        assertThat(utc).as("what the old host-derived date gave").isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(indian).as("what the calendar gives").isEqualTo(LocalDate.of(2027, 4, 1));

        TaxRuleRegistry registry = new TaxRuleRegistry();
        assertThat(registry.financialYearOf(utc)).isEqualTo("2026-2027");
        assertThat(registry.financialYearOf(indian)).isEqualTo("2027-2028");
    }

    @Test
    @DisplayName("the zone is reported, and matches the static constant")
    void zoneIsExposed() {
        assertThat(calendar.zone()).isEqualTo(MarketCalendar.ZONE);
        assertThat(MarketCalendar.ZONE).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    // ── trading days ──────────────────────────────────────────────────

    @Test
    @DisplayName("weekdays trade, weekends do not")
    void tradingDays() {
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 8, 7))).as("Friday").isTrue();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 8, 8))).as("Saturday").isFalse();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 8, 9))).as("Sunday").isFalse();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 8, 10))).as("Monday").isTrue();
    }

    // ── market hours ──────────────────────────────────────────────────

    @Test
    @DisplayName("the session runs 09:15 to 15:30 IST, boundaries included")
    void marketHoursBoundaries() {
        // Monday 10 August 2026. The boundaries are where an off-by-one would hide.
        assertThat(calendar.isMarketOpen(ist(2026, 8, 10, 9, 14))).as("09:14").isFalse();
        assertThat(calendar.isMarketOpen(ist(2026, 8, 10, 9, 15))).as("09:15 open").isTrue();
        assertThat(calendar.isMarketOpen(ist(2026, 8, 10, 12, 0))).as("midday").isTrue();
        assertThat(calendar.isMarketOpen(ist(2026, 8, 10, 15, 30))).as("15:30 close").isTrue();
        assertThat(calendar.isMarketOpen(ist(2026, 8, 10, 15, 31))).as("15:31").isFalse();
    }

    @Test
    @DisplayName("the market is shut all weekend, even during session hours")
    void closedAtWeekends() {
        assertThat(calendar.isMarketOpen(ist(2026, 8, 8, 12, 0))).as("Saturday midday").isFalse();
        assertThat(calendar.isMarketOpen(ist(2026, 8, 9, 12, 0))).as("Sunday midday").isFalse();
    }

    @Test
    @DisplayName("03:00 IST is closed, which is when a UTC-scheduled job used to run")
    void closedOvernight() {
        // The old nightly jobs fired 5.5 hours late, landing in the Indian morning. Freshness logic
        // has to know the exchange is shut here or it would re-fetch a price that cannot have moved.
        assertThat(calendar.isMarketOpen(ist(2026, 8, 10, 3, 0))).isFalse();
    }

    // ── next open ─────────────────────────────────────────────────────

    @Test
    @DisplayName("after Friday's close the next open is Monday morning, skipping the weekend")
    void nextOpenSkipsTheWeekend() {
        Instant fridayEvening = ist(2026, 8, 7, 18, 0);

        Instant next = calendar.nextOpen(fridayEvening);

        assertThat(next.atZone(MarketCalendar.ZONE).toLocalDateTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 10, 9, 15));
    }

    @Test
    @DisplayName("before the open on a trading day, the next open is later the same morning")
    void nextOpenSameDay() {
        Instant earlyMonday = ist(2026, 8, 10, 6, 0);

        assertThat(calendar.nextOpen(earlyMonday).atZone(MarketCalendar.ZONE).toLocalDateTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 10, 9, 15));
    }

    @Test
    @DisplayName("while the market is open there is nothing to wait for")
    void untilNextOpenIsZeroWhileOpen() {
        assertThat(calendar.untilNextOpen(ist(2026, 8, 10, 11, 0))).isZero();
    }

    @Test
    @DisplayName("the weekend wait is measured in days, not minutes")
    void untilNextOpenSpansTheWeekend() {
        // This is what makes an equity price legitimately "fresh" all weekend: no new price can
        // exist until Monday, so re-fetching before then is wasted quota.
        assertThat(calendar.untilNextOpen(ist(2026, 8, 7, 18, 0)).toHours())
                .isGreaterThan(60);
    }
}
