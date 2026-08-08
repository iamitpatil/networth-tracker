package com.networth.service.tax.rules;

import java.math.BigDecimal;

/**
 * Rebate under section 87A: if taxable income is at or below {@code incomeThreshold},
 * tax is reduced by up to {@code maxRebate}.
 */
public record Rebate(BigDecimal incomeThreshold, BigDecimal maxRebate) {

    public static Rebate of(String incomeThreshold, String maxRebate) {
        return new Rebate(new BigDecimal(incomeThreshold), new BigDecimal(maxRebate));
    }

    public static final Rebate NONE = new Rebate(BigDecimal.ZERO, BigDecimal.ZERO);

    /** Rebate actually available against the given income and computed tax. */
    public BigDecimal applicableTo(BigDecimal taxableIncome, BigDecimal tax) {
        if (taxableIncome.compareTo(incomeThreshold) > 0) {
            return BigDecimal.ZERO;
        }
        return tax.min(maxRebate);
    }
}
