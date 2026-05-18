package com.networth.model.enums;

/**
 * Indian Income Tax Regime selection.
 *
 * OLD regime (pre-2020): Higher tax slabs but allows deductions
 *   - Section 80C (₹1.5L), 80D, HRA, LTA, etc.
 *
 * NEW regime (default since FY 2023-24): Lower tax slabs, minimal deductions
 *   - Only standard deduction of ₹75,000 (FY 2024-25)
 *   - No 80C, no HRA exemption
 */
public enum TaxRegime {
    OLD,
    NEW
}
