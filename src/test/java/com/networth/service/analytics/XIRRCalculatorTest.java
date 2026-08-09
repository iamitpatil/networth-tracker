package com.networth.service.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class XIRRCalculatorTest {

    private final XIRRCalculator calculator = new XIRRCalculator();

    private static XIRRCalculator.CashFlow cf(String date, double amount) {
        return new XIRRCalculator.CashFlow(LocalDate.parse(date), amount);
    }

    /** XIRR is a percentage; a basis point of tolerance is ample. */
    private void assertRate(Optional<BigDecimal> actual, String expected) {
        assertThat(actual).isPresent();
        assertThat(actual.get().doubleValue())
                .isCloseTo(new BigDecimal(expected).doubleValue(), within());
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(0.0005);
    }

    // ── correctness ───────────────────────────────────────────────────

    @Test
    @DisplayName("doubling over exactly 365 days is a 100% return")
    void doublingInOneYear() {
        assertRate(calculator.calculateXIRR(List.of(
                cf("2023-01-01", -1000),
                cf("2024-01-01", 2000))), "1.0");
    }

    @Test
    @DisplayName("the 365-day convention means a leap year span is slightly over one year")
    void leapYearFollowsThe365DayConvention() {
        // 2024-01-01 to 2025-01-01 is 366 days, so doubling annualises to just under 100%,
        // matching how spreadsheet XIRR behaves. Documented so it is not read as drift.
        assertRate(calculator.calculateXIRR(List.of(
                cf("2024-01-01", -1000),
                cf("2025-01-01", 2000))), "0.9962");
    }

    @Test
    @DisplayName("no gain over any period is a 0% return")
    void flatReturn() {
        assertRate(calculator.calculateXIRR(List.of(
                cf("2024-01-01", -1000),
                cf("2025-01-01", 1000))), "0.0");
    }

    @Test
    @DisplayName("a loss produces a negative rate")
    void lossIsNegative() {
        Optional<BigDecimal> rate = calculator.calculateXIRR(List.of(
                cf("2023-01-01", -1000),
                cf("2024-01-01", 500)));
        assertThat(rate).isPresent();
        assertThat(rate.get()).isLessThan(BigDecimal.ZERO);
        assertRate(rate, "-0.5");
    }

    @Test
    @DisplayName("a 10% annual return over 365 days")
    void tenPercent() {
        assertRate(calculator.calculateXIRR(List.of(
                cf("2023-01-01", -10000),
                cf("2024-01-01", 11000))), "0.1");
    }

    @Test
    @DisplayName("multiple contributions are weighted by their timing")
    void sipStyleFlows() {
        // Twelve monthly 1,000 contributions ending at 13,000: a positive but modest rate.
        List<XIRRCalculator.CashFlow> flows = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            flows.add(cf(String.format("2024-%02d-01", m), -1000));
        }
        flows.add(cf("2025-01-01", 13000));

        Optional<BigDecimal> rate = calculator.calculateXIRR(flows);
        assertThat(rate).isPresent();
        // Money was invested for an average of about half the year, so the annualised rate
        // is well above the 8.3% simple gain.
        assertThat(rate.get()).isGreaterThan(new BigDecimal("0.10"));
        assertThat(rate.get()).isLessThan(new BigDecimal("0.25"));
    }

    @Test
    @DisplayName("unsorted input gives the same answer as sorted input")
    void unsortedInputIsHandled() {
        List<XIRRCalculator.CashFlow> sorted = List.of(
                cf("2024-01-01", -1000), cf("2024-07-01", -500), cf("2025-01-01", 1700));
        List<XIRRCalculator.CashFlow> shuffled = List.of(
                cf("2025-01-01", 1700), cf("2024-01-01", -1000), cf("2024-07-01", -500));

        assertThat(calculator.calculateXIRR(shuffled))
                .isEqualTo(calculator.calculateXIRR(sorted));
    }

    // ── the bug this rewrite fixes ────────────────────────────────────

    @Test
    @DisplayName("all-outflow cash flows return empty, not a fabricated rate")
    void allOutflowsCannotHaveARate() {
        // No money ever came back, so no rate reconciles these flows. The old code iterated
        // and returned whatever it was holding.
        assertThat(calculator.calculateXIRR(List.of(
                cf("2024-01-01", -1000),
                cf("2024-06-01", -1000),
                cf("2025-01-01", -1000)))).isEmpty();
    }

    @Test
    @DisplayName("all-inflow cash flows return empty")
    void allInflowsCannotHaveARate() {
        assertThat(calculator.calculateXIRR(List.of(
                cf("2024-01-01", 1000),
                cf("2025-01-01", 1000)))).isEmpty();
    }

    @Test
    @DisplayName("a total wipeout does not masquerade as a computed rate")
    void totalLoss() {
        // Ending at zero means the rate approaches -100%, which is outside the solvable
        // range. Empty is the honest answer.
        Optional<BigDecimal> rate = calculator.calculateXIRR(List.of(
                cf("2024-01-01", -1000),
                cf("2025-01-01", 0)));
        assertThat(rate).isEmpty();
    }

    @Test
    @DisplayName("every returned rate genuinely zeroes the net present value")
    void returnedRatesAreRealRoots() {
        List<List<XIRRCalculator.CashFlow>> cases = List.of(
                List.of(cf("2024-01-01", -1000), cf("2025-01-01", 2000)),
                List.of(cf("2020-03-15", -50000), cf("2022-08-01", -25000), cf("2026-01-10", 120000)),
                List.of(cf("2024-01-01", -1000), cf("2024-02-01", -2000), cf("2024-12-01", 3200)),
                List.of(cf("2019-01-01", -100000), cf("2026-01-01", 95000)));

        for (List<XIRRCalculator.CashFlow> flows : cases) {
            Optional<BigDecimal> rate = calculator.calculateXIRR(flows);
            assertThat(rate).as("rate for %s", flows).isPresent();

            // Recompute NPV at the reported rate; a true root drives it to ~0.
            double r = rate.get().doubleValue();
            LocalDate start = flows.stream().map(XIRRCalculator.CashFlow::date)
                    .min(LocalDate::compareTo).orElseThrow();
            double npv = 0;
            for (XIRRCalculator.CashFlow f : flows) {
                double years = java.time.temporal.ChronoUnit.DAYS.between(start, f.date()) / 365.0;
                npv += f.amount() / Math.pow(1 + r, years);
            }
            double scale = flows.stream().mapToDouble(f -> Math.abs(f.amount())).max().orElse(1);
            assertThat(Math.abs(npv)).as("NPV at %s for %s", r, flows).isLessThan(scale * 0.001);
        }
    }

    // ── degenerate input ──────────────────────────────────────────────

    @Test
    @DisplayName("null, empty and single-flow input return empty")
    void degenerateInput() {
        assertThat(calculator.calculateXIRR(null)).isEmpty();
        assertThat(calculator.calculateXIRR(List.of())).isEmpty();
        assertThat(calculator.calculateXIRR(List.of(cf("2024-01-01", -1000)))).isEmpty();
    }

    @Test
    @DisplayName("same-day flows do not divide by zero")
    void sameDayFlows() {
        // All on one date: the discount factor is 1 for every flow, so NPV is just the sum.
        // Non-zero sum means no root exists.
        assertThat(calculator.calculateXIRR(List.of(
                cf("2024-01-01", -1000),
                cf("2024-01-01", 1500)))).isEmpty();
    }

    @Test
    @DisplayName("an extreme but real gain still resolves")
    void extremeGain() {
        Optional<BigDecimal> rate = calculator.calculateXIRR(List.of(
                cf("2023-01-01", -100),
                cf("2024-01-01", 5000)));
        assertThat(rate).isPresent();
        assertRate(rate, "49.0");   // 50x in a year
    }
}
