package com.networth.service.tax.rules;

import com.networth.model.enums.AssetType;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Capital gains treatment for one effective period.
 *
 * <p>Equity rates changed mid-FY-2024-25 (23 July 2024), which is why these are held per
 * effective period rather than per financial year.
 *
 * @param ltcgRate            long-term rate on equity and equity-oriented MF
 * @param stcgRate            short-term rate on the same
 * @param ltcgExemption        annual exemption applied to long-term equity gains
 * @param cryptoRate           rate on virtual digital assets (s.115BBH)
 * @param longTermThresholdDays holding days at or above which a disposal is long-term, per asset type
 * @param defaultLongTermDays   fallback threshold for asset types not listed
 */
public record CapitalGainsRules(
        BigDecimal ltcgRate,
        BigDecimal stcgRate,
        BigDecimal ltcgExemption,
        BigDecimal cryptoRate,
        Map<AssetType, Long> longTermThresholdDays,
        long defaultLongTermDays) {

    /** Holding days at or above which a disposal of this asset type is long-term. */
    public long longTermThresholdFor(AssetType assetType) {
        Long days = longTermThresholdDays.get(assetType);
        return days != null ? days : defaultLongTermDays;
    }

    public boolean isLongTerm(AssetType assetType, long holdingDays) {
        // Crypto has no long/short distinction: a flat rate applies regardless of holding period.
        if (assetType == AssetType.CRYPTO) {
            return true;
        }
        return holdingDays >= longTermThresholdFor(assetType);
    }
}
