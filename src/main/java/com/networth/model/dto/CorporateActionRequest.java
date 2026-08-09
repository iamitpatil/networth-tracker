package com.networth.model.dto;

import com.networth.model.enums.AssetType;
import com.networth.model.enums.CorporateActionType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A bonus issue, split or demerger to apply to a holding.
 *
 * <p>Deliberately separate from {@link TransactionRequest}, for three reasons:
 * <ul>
 *   <li>a bonus share has a price of zero and a split has no price at all, so
 *       {@code TransactionRequest}'s {@code @Positive} price validation cannot hold;</li>
 *   <li>a demerger touches <b>two</b> holdings, which a single transaction cannot express;</li>
 *   <li>users think in ratios ("1:2 bonus") rather than in resulting quantities, so the ratio
 *       is what is captured and the quantities are derived — fewer chances to get it wrong.</li>
 * </ul>
 *
 * <p>Which fields matter depends on {@link #type}; the service validates the relevant ones and
 * rejects the request with a specific message rather than silently applying a partial action.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorporateActionRequest {

    @NotNull(message = "Action type is required")
    private CorporateActionType type;

    /** Ex-date / allotment date. Drives which financial year the action falls in. */
    @NotNull(message = "Action date is required")
    private LocalDate actionDate;

    private String notes;

    // ── BONUS ────────────────────────────────────────────────────────────────────

    /**
     * Bonus shares received. Read as a ratio numerator when {@link #sharesHeld} is also given
     * ("1 for every 2 held"), and as the absolute allotment when it is not — the form a demat
     * statement and a CSV import use.
     */
    private BigDecimal sharesReceived;

    /** Denominator of the bonus ratio. Leave null to treat {@link #sharesReceived} as absolute. */
    private BigDecimal sharesHeld;

    // ── SPLIT: fromQuantity shares become toQuantity shares ──────────────────────
    // 1 → 2 is a 1:2 split; 2 → 1 is a consolidation. Both are handled by the same maths.

    private BigDecimal fromQuantity;
    private BigDecimal toQuantity;

    // ── DEMERGER ──────────────────────────────────────────────────

    /** Ticker of the newly listed company. */
    private String resultingSymbol;

    /** Display name; falls back to the symbol when not given. */
    private String resultingName;

    /** Defaults to the parent holding's asset type. */
    private AssetType resultingAssetType;

    /** Shares received in the resulting company. */
    private BigDecimal resultingQuantity;

    /**
     * Percentage of the parent's cost that moves to the resulting company, per the net-asset
     * split the demerging company publishes (s.49(2C) and s.49(2D)).
     *
     * <p>This is the field people get wrong by assuming the new shares are free. They are not:
     * the original cost is <em>divided</em> between the two companies, so a nil cost here would
     * tax the entire sale proceeds of the new shares as gain.
     */
    private BigDecimal costApportionmentPercent;

    /**
     * Demat account for the resulting holding. Defaults to the parent's, which is where the
     * shares actually land in practice.
     */
    private String resultingDematAccountId;
}
