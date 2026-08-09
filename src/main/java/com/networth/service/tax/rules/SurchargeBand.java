package com.networth.service.tax.rules;

import java.math.BigDecimal;

/**
 * Surcharge on tax once total income exceeds {@code incomeAbove}.
 *
 * @param incomeAbove threshold the income must exceed for this band to apply
 * @param rate        surcharge as a fraction of tax, e.g. 0.10 for 10%
 */
public record SurchargeBand(BigDecimal incomeAbove, BigDecimal rate) {

    public static SurchargeBand of(String incomeAbove, String rate) {
        return new SurchargeBand(new BigDecimal(incomeAbove), new BigDecimal(rate));
    }
}
