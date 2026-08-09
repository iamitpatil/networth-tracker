package com.networth.service.tax.rules;

import java.math.BigDecimal;

/**
 * Chapter VI-A deduction ceilings for a financial year.
 *
 * @param limit80C      combined 80C / 80CCC / 80CCD(1) ceiling
 * @param limit80CCD1B  additional NPS deduction over and above 80C
 * @param limit80DSelf  health insurance premium, self and family
 * @param limit80DParentsSenior health insurance premium, senior-citizen parents
 */
public record DeductionLimits(
        BigDecimal limit80C,
        BigDecimal limit80CCD1B,
        BigDecimal limit80DSelf,
        BigDecimal limit80DParentsSenior) {
}
