package com.networth.service.analytics;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
public class XIRRCalculator {

    private static final int MAX_ITERATIONS = 100;
    private static final double TOLERANCE = 1e-7;

    public BigDecimal calculateXIRR(List<CashFlow> cashFlows) {
        if (cashFlows == null || cashFlows.size() < 2) {
            return BigDecimal.ZERO;
        }

        double guess = 0.1;
        double result = newtonRaphson(cashFlows, guess);

        if (Double.isNaN(result) || Double.isInfinite(result)) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(result).setScale(4, RoundingMode.HALF_UP);
    }

    private double newtonRaphson(List<CashFlow> cashFlows, double guess) {
        LocalDate startDate = cashFlows.get(0).date();

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double npv = 0;
            double derivative = 0;

            for (CashFlow cf : cashFlows) {
                double years = (double) java.time.temporal.ChronoUnit.DAYS.between(startDate, cf.date()) / 365.0;
                double discount = Math.pow(1 + guess, years);

                if (discount == 0) {
                    return guess;
                }

                npv += cf.amount() / discount;
                derivative -= cf.amount() * years / Math.pow(1 + guess, years + 1);
            }

            if (Math.abs(npv) < TOLERANCE) {
                return guess;
            }

            if (Math.abs(derivative) < TOLERANCE) {
                break;
            }

            guess = guess - npv / derivative;

            if (guess <= -1) {
                guess = 0.01;
            }
        }

        return guess;
    }

    public record CashFlow(LocalDate date, double amount) {}
}
