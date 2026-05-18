package com.networth.service.tax;

import com.networth.model.enums.TaxRegime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Computes Indian income tax based on FY 2024-25 slab rates.
 *
 * NEW Regime (Default since FY 2023-24):
 * - 0 to 3L: 0%
 * - 3L to 7L: 5%
 * - 7L to 10L: 10%
 * - 10L to 12L: 15%
 * - 12L to 15L: 20%
 * - Above 15L: 30%
 * - Standard deduction: ₹75,000
 * - Rebate u/s 87A: Up to ₹25,000 if income ≤ ₹7L (effectively no tax up to ₹7L)
 *
 * OLD Regime:
 * - 0 to 2.5L: 0%
 * - 2.5L to 5L: 5%
 * - 5L to 10L: 20%
 * - Above 10L: 30%
 * - Standard deduction: ₹50,000
 * - Rebate u/s 87A: Up to ₹12,500 if income ≤ ₹5L
 * - 80C, 80D, HRA, etc. deductions allowed
 *
 * Plus 4% Health & Education Cess on tax
 * Plus Surcharge for high incomes (>50L)
 */
@Service
@Slf4j
public class TaxRegimeCalculator {

    private static final BigDecimal CESS_RATE = new BigDecimal("0.04");

    /**
     * Calculate income tax based on tax regime and total taxable income.
     */
    public TaxComputation calculateTax(BigDecimal taxableIncome, TaxRegime regime) {
        if (taxableIncome == null || taxableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return TaxComputation.zero(regime);
        }

        BigDecimal tax;
        BigDecimal rebate;

        if (regime == TaxRegime.NEW) {
            tax = computeNewRegimeTax(taxableIncome);
            rebate = computeNewRegimeRebate(taxableIncome, tax);
        } else {
            tax = computeOldRegimeTax(taxableIncome);
            rebate = computeOldRegimeRebate(taxableIncome, tax);
        }

        BigDecimal taxAfterRebate = tax.subtract(rebate).max(BigDecimal.ZERO);
        BigDecimal surcharge = computeSurcharge(taxableIncome, taxAfterRebate);
        BigDecimal taxWithSurcharge = taxAfterRebate.add(surcharge);
        BigDecimal cess = taxWithSurcharge.multiply(CESS_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalTax = taxWithSurcharge.add(cess);

        return TaxComputation.builder()
                .regime(regime)
                .taxableIncome(taxableIncome)
                .taxBeforeRebate(tax)
                .rebate(rebate)
                .taxAfterRebate(taxAfterRebate)
                .surcharge(surcharge)
                .cess(cess)
                .totalTax(totalTax)
                .build();
    }

    /**
     * Compare both regimes and recommend the better one.
     */
    public RegimeComparison compareRegimes(
            BigDecimal grossSalary,
            BigDecimal totalDeductions,
            BigDecimal hraExemption,
            BigDecimal standardDeductionOld,
            BigDecimal standardDeductionNew) {

        // OLD regime: gross - deductions (80C, 80D, HRA, std deduction)
        BigDecimal taxableOld = grossSalary
                .subtract(standardDeductionOld != null ? standardDeductionOld : new BigDecimal("50000"))
                .subtract(hraExemption != null ? hraExemption : BigDecimal.ZERO)
                .subtract(totalDeductions != null ? totalDeductions : BigDecimal.ZERO)
                .max(BigDecimal.ZERO);

        // NEW regime: gross - standard deduction only (no other deductions)
        BigDecimal taxableNew = grossSalary
                .subtract(standardDeductionNew != null ? standardDeductionNew : new BigDecimal("75000"))
                .max(BigDecimal.ZERO);

        TaxComputation oldTax = calculateTax(taxableOld, TaxRegime.OLD);
        TaxComputation newTax = calculateTax(taxableNew, TaxRegime.NEW);

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

    // ===== NEW Regime =====

    private BigDecimal computeNewRegimeTax(BigDecimal income) {
        double inc = income.doubleValue();
        double tax = 0;

        if (inc > 1500000) {
            tax += (inc - 1500000) * 0.30;
            inc = 1500000;
        }
        if (inc > 1200000) {
            tax += (inc - 1200000) * 0.20;
            inc = 1200000;
        }
        if (inc > 1000000) {
            tax += (inc - 1000000) * 0.15;
            inc = 1000000;
        }
        if (inc > 700000) {
            tax += (inc - 700000) * 0.10;
            inc = 700000;
        }
        if (inc > 300000) {
            tax += (inc - 300000) * 0.05;
        }

        return BigDecimal.valueOf(tax).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeNewRegimeRebate(BigDecimal income, BigDecimal tax) {
        // u/s 87A: Up to ₹25,000 rebate if income ≤ ₹7L
        if (income.compareTo(new BigDecimal("700000")) <= 0) {
            return tax.min(new BigDecimal("25000"));
        }
        return BigDecimal.ZERO;
    }

    // ===== OLD Regime =====

    private BigDecimal computeOldRegimeTax(BigDecimal income) {
        double inc = income.doubleValue();
        double tax = 0;

        if (inc > 1000000) {
            tax += (inc - 1000000) * 0.30;
            inc = 1000000;
        }
        if (inc > 500000) {
            tax += (inc - 500000) * 0.20;
            inc = 500000;
        }
        if (inc > 250000) {
            tax += (inc - 250000) * 0.05;
        }

        return BigDecimal.valueOf(tax).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeOldRegimeRebate(BigDecimal income, BigDecimal tax) {
        // u/s 87A: Up to ₹12,500 rebate if income ≤ ₹5L
        if (income.compareTo(new BigDecimal("500000")) <= 0) {
            return tax.min(new BigDecimal("12500"));
        }
        return BigDecimal.ZERO;
    }

    /**
     * Surcharge on tax for high-income earners.
     * Applies to both regimes (slightly different in NEW after FY 2023-24).
     *
     * 50L < income <= 1Cr:  10% surcharge
     * 1Cr < income <= 2Cr:  15% surcharge
     * 2Cr < income <= 5Cr:  25% surcharge (OLD) / 25% (NEW, capped at 25% from FY 2023-24)
     * Above 5Cr:            37% (OLD) / 25% (NEW, capped at 25%)
     */
    private BigDecimal computeSurcharge(BigDecimal income, BigDecimal tax) {
        BigDecimal inc = income;
        double rate = 0;

        if (inc.compareTo(new BigDecimal("50000000")) > 0) {
            rate = 0.37;
        } else if (inc.compareTo(new BigDecimal("20000000")) > 0) {
            rate = 0.25;
        } else if (inc.compareTo(new BigDecimal("10000000")) > 0) {
            rate = 0.15;
        } else if (inc.compareTo(new BigDecimal("5000000")) > 0) {
            rate = 0.10;
        }

        if (rate == 0) return BigDecimal.ZERO;
        return tax.multiply(BigDecimal.valueOf(rate)).setScale(2, RoundingMode.HALF_UP);
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
