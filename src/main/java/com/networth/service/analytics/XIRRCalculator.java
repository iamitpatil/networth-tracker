package com.networth.service.analytics;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Computes the internal rate of return of dated cash flows (Excel's XIRR).
 *
 * <p>Returns an empty result when no rate can be determined, rather than a number.
 * That distinction matters: the previous implementation returned whatever value
 * Newton-Raphson happened to hold when it ran out of iterations, so a failed solve was
 * indistinguishable from a real answer. A portfolio could be shown a confident "-63.7%
 * XIRR" that meant nothing.
 *
 * <p>Two solvers are used in sequence. Newton-Raphson is fast but can diverge or oscillate,
 * so a result is accepted only if the net present value at that rate is genuinely near zero.
 * If every seed fails, a bracketing bisection takes over: it scans for a sign change in NPV
 * and closes on it, which cannot report success without having bracketed a root.
 */
@Component
public class XIRRCalculator {

    private static final int MAX_NEWTON_ITERATIONS = 100;
    private static final int MAX_BISECTION_ITERATIONS = 200;

    /** NPV is considered zero within this absolute amount, scaled by the cash flow size. */
    private static final double NPV_TOLERANCE = 1e-6;
    private static final double DERIVATIVE_FLOOR = 1e-10;

    /** Days per year, matching the XIRR convention used by spreadsheets. */
    private static final double DAYS_PER_YEAR = 365.0;

    /** A rate at or below -100% makes the discount base non-positive and is not a solution. */
    private static final double MIN_RATE = -0.9999;
    private static final double MAX_RATE = 100.0;   // 10,000%, well beyond any real portfolio

    /** Newton-Raphson seeds. Several are tried because a single seed can diverge. */
    private static final double[] SEEDS = {0.1, 0.0, 0.3, -0.3, 1.0, -0.9};

    /**
     * The annualised rate of return, or empty when none can be determined.
     *
     * <p>Empty is returned when there are fewer than two flows, when the flows are all the
     * same sign (no rate solves NPV = 0), or when neither solver converges.
     */
    public Optional<BigDecimal> calculateXIRR(List<CashFlow> cashFlows) {
        if (cashFlows == null || cashFlows.size() < 2) {
            return Optional.empty();
        }

        // Sorted defensively. The maths keys every flow off the earliest date, so unsorted
        // input previously produced negative year fractions and a meaningless result.
        List<CashFlow> flows = cashFlows.stream()
                .sorted(Comparator.comparing(CashFlow::date))
                .toList();

        boolean hasOutflow = flows.stream().anyMatch(f -> f.amount() < 0);
        boolean hasInflow = flows.stream().anyMatch(f -> f.amount() > 0);
        if (!hasOutflow || !hasInflow) {
            // With money only going one way there is no rate at which NPV is zero.
            return Optional.empty();
        }

        double scale = flows.stream().mapToDouble(f -> Math.abs(f.amount())).max().orElse(1.0);
        double tolerance = Math.max(NPV_TOLERANCE, scale * 1e-9);

        for (double seed : SEEDS) {
            Double rate = newtonRaphson(flows, seed, tolerance);
            if (rate != null) {
                return Optional.of(round(rate));
            }
        }

        Double bracketed = bisect(flows, tolerance);
        return bracketed == null ? Optional.empty() : Optional.of(round(bracketed));
    }

    /** @return the converged rate, or null if this seed did not converge to a root. */
    private Double newtonRaphson(List<CashFlow> flows, double seed, double tolerance) {
        LocalDate start = flows.get(0).date();
        double rate = seed;

        for (int i = 0; i < MAX_NEWTON_ITERATIONS; i++) {
            if (rate <= MIN_RATE || rate > MAX_RATE || Double.isNaN(rate)) {
                return null;
            }

            double npv = 0;
            double derivative = 0;
            for (CashFlow cf : flows) {
                double years = years(start, cf.date());
                double discount = Math.pow(1 + rate, years);
                if (discount == 0 || Double.isInfinite(discount) || Double.isNaN(discount)) {
                    return null;
                }
                npv += cf.amount() / discount;
                derivative -= cf.amount() * years / Math.pow(1 + rate, years + 1);
            }

            if (Math.abs(npv) < tolerance) {
                return rate;
            }
            if (Math.abs(derivative) < DERIVATIVE_FLOOR) {
                return null;   // flat gradient: this seed cannot make progress
            }

            double next = rate - npv / derivative;
            if (Double.isNaN(next) || Double.isInfinite(next)) {
                return null;
            }
            rate = next;
        }

        // Out of iterations without reaching the tolerance. Returning `rate` here is exactly
        // the bug this rewrite removes: it looks like an answer and is not one.
        return null;
    }

    /**
     * Scans for a sign change in NPV and bisects it.
     *
     * <p>Slower than Newton-Raphson but dependable: it can only return a rate once it has a
     * bracket, so it never mistakes a wandering iterate for a solution.
     */
    private Double bisect(List<CashFlow> flows, double tolerance) {
        double low = MIN_RATE;
        double npvLow = npv(flows, low);
        if (Double.isNaN(npvLow)) {
            return null;
        }

        // Geometric scan outward: fine near zero where real rates live, coarse further out.
        for (double high = -0.99; high <= MAX_RATE; high = (high < 0) ? high / 2 + 0.005 : Math.max(0.01, high * 1.5)) {
            double npvHigh = npv(flows, high);
            if (Double.isNaN(npvHigh)) {
                continue;
            }
            if (Math.abs(npvHigh) < tolerance) {
                return high;
            }
            if (Math.signum(npvLow) != Math.signum(npvHigh)) {
                return closeOn(flows, low, high, npvLow, tolerance);
            }
            low = high;
            npvLow = npvHigh;
            if (high > MAX_RATE / 2) {
                break;
            }
        }
        return null;
    }

    private Double closeOn(List<CashFlow> flows, double low, double high, double npvLow, double tolerance) {
        for (int i = 0; i < MAX_BISECTION_ITERATIONS; i++) {
            double mid = (low + high) / 2;
            double npvMid = npv(flows, mid);
            if (Double.isNaN(npvMid)) {
                return null;
            }
            if (Math.abs(npvMid) < tolerance || (high - low) < 1e-12) {
                return mid;
            }
            if (Math.signum(npvMid) == Math.signum(npvLow)) {
                low = mid;
                npvLow = npvMid;
            } else {
                high = mid;
            }
        }
        return null;
    }

    private double npv(List<CashFlow> flows, double rate) {
        if (rate <= -1) {
            return Double.NaN;
        }
        LocalDate start = flows.get(0).date();
        double total = 0;
        for (CashFlow cf : flows) {
            double discount = Math.pow(1 + rate, years(start, cf.date()));
            if (discount == 0 || Double.isInfinite(discount) || Double.isNaN(discount)) {
                return Double.NaN;
            }
            total += cf.amount() / discount;
        }
        return total;
    }

    private double years(LocalDate start, LocalDate date) {
        return ChronoUnit.DAYS.between(start, date) / DAYS_PER_YEAR;
    }

    private BigDecimal round(double rate) {
        return BigDecimal.valueOf(rate).setScale(4, RoundingMode.HALF_UP);
    }

    /** A dated cash flow. Negative is money out (a purchase), positive is money in. */
    public record CashFlow(LocalDate date, double amount) {}
}
