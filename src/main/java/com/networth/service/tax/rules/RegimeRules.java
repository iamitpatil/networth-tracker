package com.networth.service.tax.rules;

import java.math.BigDecimal;
import java.util.List;

/**
 * Income tax rules for one regime in one financial year.
 *
 * @param slabs             ordered ascending; the last entry should be open-ended
 * @param standardDeduction salary standard deduction
 * @param rebate            section 87A rebate, or {@link Rebate#NONE}
 * @param surchargeBands    ordered ascending by threshold
 * @param surchargeCap      maximum surcharge rate, or {@code null} for uncapped. The new
 *                          regime caps surcharge at 25% where the old regime reaches 37%
 * @param allowsDeductions  whether chapter VI-A deductions (80C, HRA, ...) may be claimed
 */
public record RegimeRules(
        List<Slab> slabs,
        BigDecimal standardDeduction,
        Rebate rebate,
        List<SurchargeBand> surchargeBands,
        BigDecimal surchargeCap,
        boolean allowsDeductions) {

    /** Surcharge rate for the given total income, honouring {@link #surchargeCap}. */
    public BigDecimal surchargeRateFor(BigDecimal income) {
        BigDecimal rate = BigDecimal.ZERO;
        for (SurchargeBand band : surchargeBands) {
            if (income.compareTo(band.incomeAbove()) > 0) {
                rate = band.rate();
            }
        }
        if (surchargeCap != null && rate.compareTo(surchargeCap) > 0) {
            return surchargeCap;
        }
        return rate;
    }
}
