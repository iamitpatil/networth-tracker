package com.networth.service.tax.rules;

import com.networth.model.enums.TaxRegime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Every tax rule in force for one financial year over one effective period.
 *
 * <p>Most financial years have a single period. Where a Budget changes rates mid-year — as
 * on 23 July 2024 for equity capital gains — the year holds several periods and the
 * applicable one is selected by transaction date.
 *
 * @param financialYear canonical four-digit form, e.g. "2024-2025"
 * @param effectiveFrom first date this period applies to (inclusive)
 * @param effectiveTo   last date this period applies to (inclusive)
 * @param capitalGains  capital gains treatment for this period
 * @param regimes       income tax rules per regime
 * @param deductions    chapter VI-A ceilings
 * @param cessRate      health and education cess, applied to total tax of every kind
 * @param verified      whether these figures have been checked against an authoritative
 *                      source. False means "best effort, needs confirmation" and is exposed
 *                      through the API so it cannot be mistaken for settled data
 * @param note          provenance or caveat, surfaced in tests and review
 */
public record TaxRuleSet(
        String financialYear,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        CapitalGainsRules capitalGains,
        Map<TaxRegime, RegimeRules> regimes,
        DeductionLimits deductions,
        BigDecimal cessRate,
        boolean verified,
        String note) {

    public boolean covers(LocalDate date) {
        return !date.isBefore(effectiveFrom) && !date.isAfter(effectiveTo);
    }

    /**
     * Rules for a regime in this year.
     *
     * @throws RegimeNotAvailableException if the regime did not exist in this financial year.
     *         This is a 400, not a 500: the new regime genuinely did not exist before
     *         FY 2020-21, so asking for it is a bad request rather than a server fault.
     */
    public RegimeRules regime(TaxRegime regime) {
        RegimeRules rules = regimes.get(regime);
        if (rules == null) {
            throw new RegimeNotAvailableException(regime, financialYear);
        }
        return rules;
    }
}
