package com.networth.config;

import com.networth.service.tax.rules.TaxRuleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The application's clock must read IST regardless of how the host is configured.
 *
 * <p>These tests exist because the failure they guard against is invisible: the Docker image sets
 * no {@code TZ}, so the JVM ran in UTC and every {@code LocalDate.now()} in the codebase returned
 * the previous day between midnight and 05:30 IST. Nothing threw; the wrong financial year was
 * simply reported for five and a half hours a day.
 */
class TimeZoneConfigTest {

    private final TimeZone original = TimeZone.getDefault();

    @AfterEach
    void restore() {
        TimeZone.setDefault(original);
    }

    private TimeZoneConfig configFor(String zone) {
        TimeZoneConfig config = new TimeZoneConfig();
        ReflectionTestUtils.setField(config, "timezone", zone);
        return config;
    }

    @Test
    @DisplayName("the default zone is IST once the config has run")
    void pinsIst() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        configFor("Asia/Kolkata").pinDefaultTimeZone();

        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    @DisplayName("a bad zone fails the boot rather than falling back to the host's")
    void rejectsAnInvalidZone() {
        assertThatThrownBy(() -> configFor("Asia/Kolkatta").pinDefaultTimeZone())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid zone ID");
    }

    @Test
    @DisplayName("early-morning IST resolves to the Indian date, not the lagging UTC one")
    void earlyMorningIstIsTheIndianDate() {
        // 01:00 IST on 1 April 2027 is still 31 March 2027 in UTC. Under UTC the app called that
        // the previous financial year; under IST it is the new one.
        LocalDateTime istInstant = LocalDateTime.of(2027, 4, 1, 1, 0);

        LocalDate asUtc = istInstant.atZone(ZoneId.of("Asia/Kolkata"))
                .withZoneSameInstant(ZoneId.of("UTC")).toLocalDate();
        LocalDate asIst = istInstant.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();

        assertThat(asUtc).isEqualTo(LocalDate.of(2027, 3, 31));   // the bug
        assertThat(asIst).isEqualTo(LocalDate.of(2027, 4, 1));    // the fix

        TaxRuleRegistry registry = new TaxRuleRegistry();
        assertThat(registry.financialYearOf(asUtc)).isEqualTo("2026-2027");
        assertThat(registry.financialYearOf(asIst)).isEqualTo("2027-2028");
    }

    @Test
    @DisplayName("every cron schedule names its zone explicitly")
    void everyCronPinsItsZone() throws Exception {
        // Belt and braces: TimeZoneConfig fixes the default, but a cron without an explicit zone
        // is silently host-dependent if this class is ever removed or reordered. Scanning the
        // source keeps a new schedule from being added without one.
        java.nio.file.Path root = java.nio.file.Path.of("src/main/java");
        try (var files = java.nio.file.Files.walk(root)) {
            var offenders = files
                    .filter(f -> f.toString().endsWith(".java"))
                    .flatMap(f -> {
                        try {
                            return java.nio.file.Files.readAllLines(f).stream()
                                    .filter(l -> l.contains("@Scheduled(cron"))
                                    .filter(l -> !l.contains("zone"))
                                    .map(l -> f.getFileName() + ": " + l.trim());
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .toList();
            assertThat(offenders).as("cron schedules without an explicit zone").isEmpty();
        }
    }
}
