package com.networth.service.tax.rules;

import java.util.Collection;

/**
 * Thrown when tax rules have not been defined for the requested financial year.
 *
 * <p>Deliberately an error rather than a silent fallback to another year's rates: applying
 * the wrong year's rates produces a plausible-looking but incorrect tax figure, which is
 * far harder to notice than a rejected request.
 */
public class UnsupportedFinancialYearException extends IllegalArgumentException {

    public UnsupportedFinancialYearException(String financialYear, Collection<String> supported) {
        super("No tax rules defined for financial year '" + financialYear
                + "'. Supported years: " + String.join(", ", supported));
    }
}
