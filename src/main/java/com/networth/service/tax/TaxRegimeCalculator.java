package com.networth.service.tax;

import com.networth.model.enums.TaxRegime;
import com.networth.service.tax.rules.RegimeRules;
import com.networth.service.tax.rules.Slab;
import com.networth.service.tax.rules.TaxRuleRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes Indian income tax for a given financial year and regime.
 *
 * <p>Slabs, standard deductions, the section 87A rebate and surcharge bands are not defined
 * here — they come from {@link TaxRuleRegistry}, which holds them per financial year and per
 * regime. This class only applies them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaxRegimeCalculator {

    private final TaxRuleRegistry ruleRegistry;

    /**
     * Income tax for a taxable income under one regime, using the rules in force for the
     * given financial year.
     */
    public TaxComputation calculateTax(BigDecimal taxableIncome, TaxRegime regime, String financialYear) {
        var ruleSet = ruleRegistry.forFinancialYear(financialYear);
        RegimeRules rules = ruleSet.regime(regime);
        if (taxableIncome == null || taxableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return TaxComputation.zero(regime);
        }

        BigDecimal tax = applySlabs(taxableIncome, rules.slabs());
        BigDecimal rebate = rules.rebate().applicableTo(taxableIncome, tax);
        BigDecimal taxAfterRebate = tax.subtract(rebate).max(BigDecimal.ZERO);

        BigDecimal surchargeRate = rules.surchargeRateFor(taxableIncome);
        BigDecimal surcharge = taxAfterRebate.multiply(surchargeRate).setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxWithSurcharge = taxAfterRebate.add(surcharge);
        BigDecimal cess = taxWithSurcharge.multiply(ruleSet.cessRate()).setScale(2, RoundingMode.HALF_UP);

        return TaxComputation.builder()
                .regime(regime)
                .taxableIncome(taxableIncome)
                .taxBeforeRebate(tax)
                .rebate(rebate)
                .taxAfterRebate(taxAfterRebate)
                .surcharge(surcharge)
                .cess(cess)
                .totalTax(taxWithSurcharge.add(cess))
                .build();
    }

    /**
     * Compare both regimes for a financial year and recommend the cheaper one.
     *
     * <p>Standard deductions default to the year's statutory values rather than to constants,
     * so passing null gives the correct figure for the year being compared.
     */
    public RegimeComparison compareRegimes(
            BigDecimal grossSalary,
            BigDecimal totalDeductions,
            BigDecimal hraExemption,
            BigDecimal standardDeductionOld,
            BigDecimal standardDeductionNew,
            String financialYear) {

        RegimeRules oldRules = ruleRegistry.forFinancialYear(financialYear).regime(TaxRegime.OLD);
        RegimeRules newRules = ruleRegistry.forFinancialYear(financialYear).regime(TaxRegime.NEW);

        BigDecimal stdOld = standardDeductionOld != null ? standardDeductionOld : oldRules.standardDeduction();
        BigDecimal stdNew = standardDeductionNew != null ? standardDeductionNew : newRules.standardDeduction();

        // Old regime allows chapter VI-A deductions and HRA; the new regime does not.
        BigDecimal taxableOld = grossSalary
                .subtract(stdOld)
                .subtract(hraExemption != null ? hraExemption : BigDecimal.ZERO)
                .subtract(totalDeductions != null ? totalDeductions : BigDecimal.ZERO)
                .max(BigDecimal.ZERO);

        BigDecimal taxableNew = grossSalary
                .subtract(stdNew)
                .max(BigDecimal.ZERO);

        TaxComputation oldTax = calculateTax(taxableOld, TaxRegime.OLD, financialYear);
        TaxComputation newTax = calculateTax(taxableNew, TaxRegime.NEW, financialYear);

        TaxRegime recommended = oldTax.getTotalTax().compareTo(newTax.getTotalTax()) <= 0
                ? TaxRegime.OLD : TaxRegime.NEW;
        BigDecimal savings = oldTax.getTotalTax().subtract(newTax.getTotalTax()).abs();

        return RegimeComparison.builder()
                .oldRegime(oldTax)
                .newRegime(newTax)
                .recommended(recommended)
                .savings(savings)
                .build();
    }

    /**
     * Applies progressive slabs in BigDecimal.
     *
     * <p>Replaces two hand-rolled descending loops that accumulated tax in {@code double}.
     * Slabs are marginal: only the income falling inside a band is taxed at that band's rate.
     */
    BigDecimal applySlabs(BigDecimal income, List<Slab> slabs) {
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lowerBound = BigDecimal.ZERO;

        for (Slab slab : slabs) {
            if (income.compareTo(lowerBound) <= 0) {
                break;
            }
            BigDecimal upperBound = slab.isOpenEnded() ? income : slab.upTo().min(income);
            BigDecimal amountInBand = upperBound.subtract(lowerBound).max(BigDecimal.ZERO);
            tax = tax.add(amountInBand.multiply(slab.rate()));
            if (!slab.isOpenEnded()) {
                lowerBound = slab.upTo();
            }
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    // ===== DTOs =====

    @lombok.Builder
    @lombok.Getter
    public static class TaxComputation {
        private TaxRegime regime;
        private BigDecimal taxableIncome;
        private BigDecimal taxBeforeRebate;
        private BigDecimal rebate;
        private BigDecimal taxAfterRebate;
        private BigDecimal surcharge;
        private BigDecimal cess;
        private BigDecimal totalTax;

        public static TaxComputation zero(TaxRegime regime) {
            return TaxComputation.builder()
                    .regime(regime)
                    .taxableIncome(BigDecimal.ZERO)
                    .taxBeforeRebate(BigDecimal.ZERO)
                    .rebate(BigDecimal.ZERO)
                    .taxAfterRebate(BigDecimal.ZERO)
                    .surcharge(BigDecimal.ZERO)
                    .cess(BigDecimal.ZERO)
                    .totalTax(BigDecimal.ZERO)
                    .build();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("regime", regime.name());
            map.put("taxableIncome", taxableIncome);
            map.put("taxBeforeRebate", taxBeforeRebate);
            map.put("rebate", rebate);
            map.put("taxAfterRebate", taxAfterRebate);
            map.put("surcharge", surcharge);
            map.put("cess", cess);
            map.put("totalTax", totalTax);
            return map;
        }
    }

    @lombok.Builder
    @lombok.Getter
    public static class RegimeComparison {
        private TaxComputation oldRegime;
        private TaxComputation newRegime;
        private TaxRegime recommended;
        private BigDecimal savings;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("oldRegime", oldRegime.toMap());
            map.put("newRegime", newRegime.toMap());
            map.put("recommended", recommended.name());
            map.put("savings", savings);
            return map;
        }
    }
}
