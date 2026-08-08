package com.networth.service.tax.rules;

import java.math.BigDecimal;

/**
 * One income tax slab: everything up to {@code upTo} is taxed at {@code rate}.
 *
 * @param upTo upper bound of the slab, or {@code null} for the open-ended top slab
 * @param rate as a fraction, e.g. 0.05 for 5%
 */
public record Slab(BigDecimal upTo, BigDecimal rate) {

    public static Slab of(String upTo, String rate) {
        return new Slab(new BigDecimal(upTo), new BigDecimal(rate));
    }

    /** The open-ended top slab ("and above"). */
    public static Slab above(String rate) {
        return new Slab(null, new BigDecimal(rate));
    }

    public boolean isOpenEnded() {
        return upTo == null;
    }
}
