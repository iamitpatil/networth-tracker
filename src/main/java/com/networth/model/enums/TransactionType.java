package com.networth.model.enums;

import java.util.Set;

/**
 * What a transaction row represents.
 *
 * <p>The {@code cashEffect()} classification below is the single place that decides whether a row
 * moved money, and in which direction. Return calculations (XIRR, CAGR) must go through it rather
 * than testing types themselves: a corporate action changes what a position is worth without any
 * money changing hands, so counting one as a cash flow invents a return that never happened. A
 * bonus issue read as a purchase, for example, would show as money leaving your pocket for shares
 * that were free.
 */
public enum TransactionType {

    // ── Trades and income ─────────────────────────────────────────────

    BUY(CashEffect.OUTFLOW),
    SELL(CashEffect.INFLOW),
    DIVIDEND(CashEffect.INFLOW),
    INTEREST(CashEffect.INFLOW),
    SIP(CashEffect.OUTFLOW),
    LUMPSUM(CashEffect.OUTFLOW),

    /**
     * Shares moved between accounts. No money changes hands and no gain arises: a transfer is
     * not a disposal, so the original cost and holding period travel with the shares.
     */
    TRANSFER_IN(CashEffect.NONE),
    TRANSFER_OUT(CashEffect.NONE),

    // ── Corporate actions ────────────────────────────────────────────
    // No money changes hands in any of these, so they must never be treated as cash flows
    // when computing returns.

    /** Free shares issued on an existing holding. Nil cost of acquisition (s.55(2)(aa)). */
    BONUS(CashEffect.NONE),

    /**
     * Share split or consolidation. Total cost is unchanged; each existing lot's quantity is
     * scaled by the ratio and its per-share cost scaled inversely.
     */
    SPLIT(CashEffect.NONE),

    /**
     * Shares received in the resulting company of a demerger, carrying the portion of the
     * original cost apportioned to it (s.49(2C)). Its holding period includes the time the
     * original shares were held (s.2(42A)), which is why acquisitionDate is set.
     */
    DEMERGER_IN(CashEffect.NONE),

    /**
     * The matching reduction in the source holding's cost. Quantity is unchanged -- you keep
     * your original shares -- only the cost carried by earlier lots is scaled down.
     */
    DEMERGER_OUT(CashEffect.NONE),

    // ── Costs ────────────────────────────────────────────────────────

    /** Brokerage, stamp duty and the like: real money out, but not an investment. */
    FEE(CashEffect.OUTFLOW),

    /** Tax deducted or paid on the holding: money out. */
    TAX(CashEffect.OUTFLOW);

    /** Which way money moved, if at all. */
    public enum CashEffect { INFLOW, OUTFLOW, NONE }

    /** Types that add to a position: each creates an acquisition lot. */
    private static final Set<TransactionType> ACQUISITIONS =
            Set.of(BUY, SIP, LUMPSUM, BONUS, DEMERGER_IN, TRANSFER_IN);

    private final CashEffect cashEffect;

    TransactionType(CashEffect cashEffect) {
        this.cashEffect = cashEffect;
    }

    public CashEffect cashEffect() {
        return cashEffect;
    }

    /** True when this row moved money, and so belongs in a return calculation. */
    public boolean isCashFlow() {
        return cashEffect != CashEffect.NONE;
    }

    /** True when money left the investor: a purchase, a fee, tax paid. */
    public boolean isOutflow() {
        return cashEffect == CashEffect.OUTFLOW;
    }

    /** True when money came back: a sale, a dividend, interest. */
    public boolean isInflow() {
        return cashEffect == CashEffect.INFLOW;
    }

    /** True when this row buys or receives units, so a lot starts here. */
    public boolean isAcquisition() {
        return ACQUISITIONS.contains(this);
    }

    /** True for a bonus, split or demerger leg. */
    public boolean isCorporateAction() {
        return this == BONUS || this == SPLIT || this == DEMERGER_IN || this == DEMERGER_OUT;
    }
}
