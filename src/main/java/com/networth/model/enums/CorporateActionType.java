package com.networth.model.enums;

/**
 * Corporate actions that change a position without any cash changing hands.
 *
 * <p>Each is recorded as one or more {@link TransactionType} rows, so the ledger stays the
 * single history of what happened to a holding:
 *
 * <ul>
 *   <li>{@link #BONUS} → one {@code BONUS} row (free shares, nil cost of acquisition)</li>
 *   <li>{@link #SPLIT} → one {@code SPLIT} row carrying the cost adjustment factor</li>
 *   <li>{@link #DEMERGER} → a {@code DEMERGER_OUT} row on the parent and a
 *       {@code DEMERGER_IN} row on the resulting company</li>
 * </ul>
 */
public enum CorporateActionType {

    /** Free additional shares issued against an existing holding. */
    BONUS,

    /** Shares sub-divided or consolidated; the total cost is unchanged. */
    SPLIT,

    /** A business is spun off into a separately listed company. */
    DEMERGER
}
