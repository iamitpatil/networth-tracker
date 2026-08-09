package com.networth.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Pins the JVM's default time zone, so "today" means the same thing wherever the app runs.
 *
 * <p>This application is India-only: tax years run 1 April to 31 March IST, NAVs publish around
 * 23:00 IST, and every schedule in the codebase was written with IST clock times in mind. None of
 * that was actually true at runtime. The Docker image sets no {@code TZ} and has no
 * {@code /etc/timezone}, so the JVM defaulted to UTC, which broke two things:
 *
 * <ul>
 *   <li><b>"Today" was wrong for five and a half hours a day.</b> Between 00:00 and 05:30 IST the
 *       UTC date is still yesterday, so {@code LocalDate.now()} — used in 70-odd places — returned
 *       the previous day. On 1 April that meant
 *       {@code TaxRuleRegistry.currentFinancialYear()} reported the <em>previous</em> financial
 *       year, and a transaction entered at 01:00 IST was dated a day early, landing it in the
 *       wrong year's capital gains.</li>
 *   <li><b>Every cron fired 5.5 hours late.</b> The NAV refresh set for 23:30 ran at 05:00 IST the
 *       next morning; the nightly net-worth snapshot at 01:00 ran at 06:30 IST.</li>
 * </ul>
 *
 * <p>Setting this in code rather than only via {@code TZ} in {@code docker-compose.yml} means the
 * behaviour travels with the application: running the jar directly, or in CI, or on a laptop in
 * another zone, gives the same answers. The compose file still sets {@code TZ} so that container
 * logs and shell commands agree with the application.
 *
 * <h2>Why stored timestamps do not shift</h2>
 * The {@code LocalDateTime} entity fields map to {@code TIMESTAMP WITH TIME ZONE} columns, so
 * Hibernate needs a zone to convert between the two. Left to the JVM default, moving that default
 * to IST would re-interpret every existing row and shift it by 5.5 hours — enough to move a
 * late-evening timestamp onto the next calendar day. {@code hibernate.jdbc.time_zone=UTC} in
 * {@code application.properties} pins the mapping instead, so a {@code LocalDateTime} round-trips
 * to the database unchanged and this class only affects what the clock reads.
 */
@Configuration
@Slf4j
public class TimeZoneConfig {

    /** Overridable for tests or a future non-India deployment; the default is the real intent. */
    @Value("${app.timezone:Asia/Kolkata}")
    private String timezone;

    @PostConstruct
    public void pinDefaultTimeZone() {
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (RuntimeException e) {
            // A typo here would silently reintroduce the bug this class exists to fix, so fail
            // the boot rather than fall back to whatever the host happens to use.
            throw new IllegalStateException("app.timezone '" + timezone
                    + "' is not a valid zone ID (expected something like Asia/Kolkata)", e);
        }
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        log.info("Default time zone pinned to {} — dates and schedules now follow IST", zone);
    }
}
