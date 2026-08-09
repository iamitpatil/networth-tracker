package com.networth.service.tax.rules;

import com.networth.model.enums.TaxRegime;

/**
 * Thrown when a tax regime did not exist in the requested financial year — for example the
 * new regime under s.115BAC, which begins FY 2020-21.
 *
 * <p>Extends {@link IllegalArgumentException} so it surfaces as a 400: the caller asked for
 * something that cannot exist, which is a bad request rather than a server fault.
 */
public class RegimeNotAvailableException extends IllegalArgumentException {

    public RegimeNotAvailableException(TaxRegime regime, String financialYear) {
        super("The " + regime + " tax regime is not available for financial year " + financialYear
                + (regime == TaxRegime.NEW
                ? ". The new regime under section 115BAC begins FY 2020-2021."
                : "."));
    }
}
