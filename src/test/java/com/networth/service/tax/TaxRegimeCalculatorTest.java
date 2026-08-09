package com.networth.service.tax;

import com.networth.model.enums.TaxRegime;
import com.networth.service.tax.rules.Slab;
import com.networth.service.tax.rules.TaxRuleRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxRegimeCalculatorTest {

    private final TaxRuleRegistry registry = new TaxRuleRegistry();
    private final TaxRegimeCalculator calculator = new TaxRegimeCalculator(registry);

    // ── slab application ──────────────────────────────────────────────

    @Test
    @DisplayName("slabs are marginal: only income inside a band is taxed at that band's rate")
    void slabsAreMarginal() {
        List<Slab> slabs = List.of(
                Slab.of("300000", "0"),
                Slab.of("700000", "0.05"),
                Slab.above("0.10"));

        assertThat(calculator.applySlabs(new BigDecimal("300000"), slabs)).isEqualByComparingTo("0");
        // 400000 in the 5% band
        assertThat(calculator.applySlabs(new BigDecimal("700000"), slabs)).isEqualByComparingTo("20000");
        // ...plus 100000 in the open-ended 10% band
        assertThat(calculator.applySlabs(new BigDecimal("800000"), slabs)).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("slab arithmetic is exact - no double rounding drift")
    void slabArithmeticIsExact() {
        List<Slab> slabs = List.of(Slab.of("100", "0"), Slab.above("0.30"));
        // 0.1 + 0.2 in binary floating point is famously not 0.3.
        assertThat(calculator.applySlabs(new BigDecimal("100.30"), slabs))
                .isEqualByComparingTo("0.09");
    }

    // ── FY 2024-25 ────────────────────────────────────────────────────

    @Test
    @DisplayName("FY 2024-25 new regime: income at the 7L rebate cliff pays no tax")
    void fy2024NewRegimeRebateCliff() {
        var atCliff = calculator.calculateTax(new BigDecimal("700000"), TaxRegime.NEW, "2024-2025");
        // Slabs give 20000; the 87A rebate of up to 25000 wipes it out.
        assertThat(atCliff.getTaxBeforeRebate()).isEqualByComparingTo("20000");
        assertThat(atCliff.getTotalTax()).isEqualByComparingTo("0");

        // A rupee over the threshold loses the rebate entirely - the cliff is real.
        var justOver = calculator.calculateTax(new BigDecimal("700001"), TaxRegime.NEW, "2024-2025");
        assertThat(justOver.getRebate()).isEqualByComparingTo("0");
        assertThat(justOver.getTotalTax()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("FY 2024-25 old regime: 5L rebate cliff, 2.5L nil band")
    void fy2024OldRegimeRebateCliff() {
        var atCliff = calculator.calculateTax(new BigDecimal("500000"), TaxRegime.OLD, "2024-2025");
        assertThat(atCliff.getTaxBeforeRebate()).isEqualByComparingTo("12500");
        assertThat(atCliff.getTotalTax()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("cess is 4% of tax plus surcharge")
    void cessApplied() {
        // 10L under FY 2024-25 NEW: 0 + 20000 (3-7L) + 30000 (7-10L) = 50000, no rebate, no surcharge.
        var tax = calculator.calculateTax(new BigDecimal("1000000"), TaxRegime.NEW, "2024-2025");
        assertThat(tax.getTaxAfterRebate()).isEqualByComparingTo("50000");
        assertThat(tax.getCess()).isEqualByComparingTo("2000.00");
        assertThat(tax.getTotalTax()).isEqualByComparingTo("52000.00");
    }

    // ── FY 2025-26 ────────────────────────────────────────────────────

    @Test
    @DisplayName("FY 2025-26 new regime: 12L pays no tax after the enlarged 87A rebate")
    void fy2025NewRegimeTwelveLakhIsFree() {
        var tax = calculator.calculateTax(new BigDecimal("1200000"), TaxRegime.NEW, "2025-2026");
        // Slabs: 4-8L at 5% = 20000, 8-12L at 10% = 40000, so 60000 before rebate.
        assertThat(tax.getTaxBeforeRebate()).isEqualByComparingTo("60000");
        assertThat(tax.getRebate()).isEqualByComparingTo("60000");
        assertThat(tax.getTotalTax()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("the same income is taxed differently across years - the whole point of the registry")
    void ratesDifferByYear() {
        BigDecimal income = new BigDecimal("1200000");
        var fy2024 = calculator.calculateTax(income, TaxRegime.NEW, "2024-2025");
        var fy2025 = calculator.calculateTax(income, TaxRegime.NEW, "2025-2026");
        assertThat(fy2024.getTotalTax()).isGreaterThan(fy2025.getTotalTax());
        assertThat(fy2025.getTotalTax()).isEqualByComparingTo("0");
    }

    // ── marginal relief ───────────────────────────────────────────────

    @Test
    @DisplayName("just above the 12L rebate cliff, tax cannot exceed the extra income earned")
    void rebateMarginalRelief() {
        // FY 2025-26: 12,00,000 pays nothing. Without relief 12,10,000 would owe 61,500 on
        // 10,000 of extra income - earning more would leave the taxpayer worse off.
        var atThreshold = calculator.calculateTax(new BigDecimal("1200000"), TaxRegime.NEW, "2025-2026");
        assertThat(atThreshold.getTotalTax()).isEqualByComparingTo("0");

        var justOver = calculator.calculateTax(new BigDecimal("1210000"), TaxRegime.NEW, "2025-2026");
        assertThat(justOver.getTaxBeforeRebate()).isEqualByComparingTo("61500");
        assertThat(justOver.getMarginalRelief()).isEqualByComparingTo("51500");
        assertThat(justOver.getTaxAfterRebate()).isEqualByComparingTo("10000");   // capped at the excess
        // Cess still applies on top of the relieved figure.
        assertThat(justOver.getTotalTax()).isEqualByComparingTo("10400.00");
    }

    @Test
    @DisplayName("relief removes the rebate cliff, leaving only the cess charged on relieved tax")
    void reliefRemovesTheCliff() {
        // Marginal relief caps tax at the excess income, but 4% cess still applies on top of
        // that figure - which is how the published worked example arrives at 10,400 on
        // 12,10,000. So take-home can still dip, by the cess and no more, rather than by the
        // tens of thousands an unrelieved cliff would cost.
        BigDecimal threshold = new BigDecimal("1200000");
        BigDecimal takeHomeAtThreshold = threshold.subtract(
                calculator.calculateTax(threshold, TaxRegime.NEW, "2025-2026").getTotalTax());

        for (int income = 1205000; income <= 1290000; income += 5000) {
            BigDecimal gross = new BigDecimal(income);
            var tax = calculator.calculateTax(gross, TaxRegime.NEW, "2025-2026");
            BigDecimal takeHome = gross.subtract(tax.getTotalTax());
            BigDecimal maxDip = gross.subtract(threshold).multiply(new BigDecimal("0.04"));

            assertThat(takeHome).as("take-home at %d", income)
                    .isGreaterThanOrEqualTo(takeHomeAtThreshold.subtract(maxDip));
        }
    }

    @Test
    @DisplayName("relieved tax before cess equals exactly the income earned over the threshold")
    void relievedTaxEqualsTheExcess() {
        for (int excess : new int[]{5000, 10000, 20000}) {
            BigDecimal income = new BigDecimal(1200000 + excess);
            var tax = calculator.calculateTax(income, TaxRegime.NEW, "2025-2026");
            assertThat(tax.getTaxAfterRebate()).as("relieved tax at excess %d", excess)
                    .isEqualByComparingTo(new BigDecimal(excess));
        }
    }

    @Test
    @DisplayName("relief stops applying once the excess income exceeds the tax")
    void reliefIsSelfLimiting() {
        // Far enough above the threshold, the full tax is due and no relief remains.
        var wellOver = calculator.calculateTax(new BigDecimal("1400000"), TaxRegime.NEW, "2025-2026");
        assertThat(wellOver.getMarginalRelief()).isEqualByComparingTo("0");
        assertThat(wellOver.getTaxAfterRebate()).isEqualByComparingTo("90000");
    }

    @Test
    @DisplayName("just above a surcharge threshold, relief caps the jump to the excess plus cess")
    void surchargeMarginalRelief() {
        // Crossing 50 lakh adds 10% surcharge to the whole bill. Unrelieved, 10,000 of extra
        // income would attract a far larger increase in tax.
        BigDecimal threshold = new BigDecimal("5000000");
        BigDecimal justOverIncome = new BigDecimal("5010000");
        var atThreshold = calculator.calculateTax(threshold, TaxRegime.NEW, "2025-2026");
        var justOver = calculator.calculateTax(justOverIncome, TaxRegime.NEW, "2025-2026");

        assertThat(justOver.getMarginalRelief()).isGreaterThan(BigDecimal.ZERO);

        // The extra tax must not exceed the extra income, give or take the cess on it.
        BigDecimal extraTax = justOver.getTotalTax().subtract(atThreshold.getTotalTax());
        BigDecimal extraIncome = justOverIncome.subtract(threshold);
        assertThat(extraTax).isLessThanOrEqualTo(extraIncome.multiply(new BigDecimal("1.04")));

        // Sanity: without relief the surcharge alone would be about 10% of a large tax bill.
        assertThat(extraTax).isLessThan(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("years with no 87A rebate get no rebate relief")
    void noReliefWhereNoRebateExisted() {
        // FY 2010-11 had no section 87A rebate at all.
        var tax = calculator.calculateTax(new BigDecimal("500000"), TaxRegime.OLD, "2010-2011");
        assertThat(tax.getRebate()).isEqualByComparingTo("0");
        assertThat(tax.getMarginalRelief()).isEqualByComparingTo("0");
    }

    // ── surcharge ─────────────────────────────────────────────────────

    @Test
    @DisplayName("surcharge kicks in above 50L and is capped at 25% in the new regime")
    void surchargeBandsAndCap() {
        var noSurcharge = calculator.calculateTax(new BigDecimal("4000000"), TaxRegime.NEW, "2024-2025");
        assertThat(noSurcharge.getSurcharge()).isEqualByComparingTo("0");

        var tenPercent = calculator.calculateTax(new BigDecimal("6000000"), TaxRegime.NEW, "2024-2025");
        assertThat(tenPercent.getSurcharge())
                .isEqualByComparingTo(tenPercent.getTaxAfterRebate().multiply(new BigDecimal("0.10")).setScale(2));

        // Above 5Cr the old regime reaches 37% while the new regime is capped at 25%.
        BigDecimal veryHigh = new BigDecimal("60000000");
        var oldRegime = calculator.calculateTax(veryHigh, TaxRegime.OLD, "2024-2025");
        var newRegime = calculator.calculateTax(veryHigh, TaxRegime.NEW, "2024-2025");
        assertThat(oldRegime.getSurcharge())
                .isEqualByComparingTo(oldRegime.getTaxAfterRebate().multiply(new BigDecimal("0.37")).setScale(2));
        assertThat(newRegime.getSurcharge())
                .isEqualByComparingTo(newRegime.getTaxAfterRebate().multiply(new BigDecimal("0.25")).setScale(2));
    }

    // ── regime comparison ─────────────────────────────────────────────

    @Test
    @DisplayName("comparison defaults each regime's standard deduction to that year's statutory value")
    void comparisonUsesYearsStandardDeductions() {
        // Passing nulls must not fall back to hardcoded 50k/75k - it must read the year.
        var comparison = calculator.compareRegimes(
                new BigDecimal("1500000"), new BigDecimal("150000"), BigDecimal.ZERO,
                null, null, "2025-2026");

        // NEW: 1500000 - 75000 = 1425000 taxable. OLD: 1500000 - 50000 - 150000 = 1300000.
        assertThat(comparison.getNewRegime().getTaxableIncome()).isEqualByComparingTo("1425000");
        assertThat(comparison.getOldRegime().getTaxableIncome()).isEqualByComparingTo("1300000");
        assertThat(comparison.getRecommended()).isNotNull();
        assertThat(comparison.getSavings()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("an unsupported financial year is rejected rather than silently substituted")
    void unsupportedYearRejected() {
        assertThatThrownBy(() -> calculator.calculateTax(new BigDecimal("1000000"), TaxRegime.NEW, "1999-2000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1999-2000");
    }

    @Test
    @DisplayName("zero or negative income yields no tax")
    void zeroIncome() {
        assertThat(calculator.calculateTax(BigDecimal.ZERO, TaxRegime.NEW, "2024-2025").getTotalTax())
                .isEqualByComparingTo("0");
        assertThat(calculator.calculateTax(null, TaxRegime.OLD, "2024-2025").getTotalTax())
                .isEqualByComparingTo("0");
    }
}
