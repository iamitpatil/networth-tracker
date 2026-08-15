package com.networth.service.market.provider;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One corporate action as the exchange published it: a dividend, a bonus, a split or a demerger.
 *
 * <p>Supersedes {@link DividendEvent} for the event sync, because NSE returns all of these from a single
 * request and filtering to dividends threw away the rest. Two facts made that expensive: bonuses and
 * splits were then invisible, so twelve transactions in the live database sat typed as {@code BUY} at a
 * price of zero; and re-fetching them separately would have doubled a four-hour sync at NSE's ten
 * requests a minute.
 *
 * <p>{@code amountPerShare} carries a dividend's payout and {@code ratio} carries a bonus or split
 * factor. Exactly one of the two is meaningful for any given event, which is why both are nullable rather
 * than folded into one field with a type flag to interpret it.
 */
@Value
@Builder
public class CorporateActionEvent {

    /** DIVIDEND, BONUS, SPLIT, DEMERGER, RIGHTS. */
    String eventType;

    /** Interim / Final / Special for a dividend; empty otherwise. Never null — it is part of the key. */
    String eventSubtype;

    String symbol;
    String isin;

    /** Rupees per share, for a dividend. Null for every other type. */
    BigDecimal amountPerShare;

    /**
     * How the share count is multiplied.
     *
     * <p>2.0 for a 1:1 bonus (one new share per share held, so the holding doubles), 1.5 for 1:2, 2.0 for
     * a face-value split from Rs 2 to Re 1. Null for a dividend, and for a demerger, whose entitlement
     * ratio is a relationship between two different companies rather than a multiplier on one.
     */
    BigDecimal ratio;

    LocalDate exDate;
    LocalDate recordDate;
    String description;
    String source;
}
