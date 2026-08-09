package com.networth.service.tax;

import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.TransactionRepository;
import com.networth.service.tax.rules.CapitalGainsRules;
import com.networth.service.tax.rules.TaxRuleRegistry;
import com.networth.service.tax.rules.TaxRuleSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CapitalGainsCalculator {

    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    /** Per-year, per-period tax rules. See {@link TaxRuleRegistry} for why they live in code. */
    private final TaxRuleRegistry ruleRegistry;

    @Transactional(readOnly = true)
    public Map<String, Object> calculateCapitalGains(UUID userId, String financialYear) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        LocalDate fyStart = ruleRegistry.startOf(financialYear);
        LocalDate fyEnd = ruleRegistry.endOf(financialYear);
        // Fail fast if we have no rules for the year, rather than computing with another
        // year's rates and returning a plausible but wrong figure.
        ruleRegistry.forFinancialYear(financialYear);

        List<CapitalGain> equityGains = new ArrayList<>();
        List<CapitalGain> debtGains = new ArrayList<>();
        List<CapitalGain> goldGains = new ArrayList<>();
        List<CapitalGain> cryptoGains = new ArrayList<>();
        List<CapitalGain> realEstateGains = new ArrayList<>();

        // One query for the whole portfolio, grouped in memory. This was a query per holding
        // (originally two), so a 60-holding portfolio issued 60+ queries per tax report.
        Map<UUID, List<Transaction>> txnsByHolding = transactionRepository.findByUserId(userId).stream()
                .filter(t -> t.getHoldingId() != null && t.getTransactionDate() != null)
                .collect(Collectors.groupingBy(Transaction::getHoldingId));

        for (Holding holding : holdings) {
            List<Transaction> txns = txnsByHolding.getOrDefault(holding.getId(), List.of());

            List<Transaction> sells = txns.stream()
                    .filter(t -> t.getTransactionType() == TransactionType.SELL)
                    .filter(t -> {
                        LocalDate txnDate = t.getTransactionDate().toLocalDate();
                        return !txnDate.isBefore(fyStart) && !txnDate.isAfter(fyEnd);
                    })
                    .sorted(Comparator.comparing(Transaction::getTransactionDate))
                    .toList();

            // FIFO requires the buy lots in chronological order, and sells applied in
            // chronological order, so that earlier sells consume the earliest lots.
            Deque<Lot> buyLots = txns.stream()
                    .filter(t -> t.getTransactionType() == TransactionType.BUY
                            || t.getTransactionType() == TransactionType.SIP
                            || t.getTransactionType() == TransactionType.LUMPSUM)
                    .sorted(Comparator.comparing(Transaction::getTransactionDate))
                    .map(Lot::new)
                    .collect(Collectors.toCollection(ArrayDeque::new));

            for (Transaction sell : sells) {
                BigDecimal remainingQty = sell.getQuantity().abs();

                while (remainingQty.compareTo(BigDecimal.ZERO) > 0 && !buyLots.isEmpty()) {
                    Lot lot = buyLots.peek();

                    // Match against what is LEFT in this lot. Reading the transaction's
                    // original quantity here would let a partially consumed lot be drawn
                    // down more than once, over-reporting cheap early lots.
                    BigDecimal matchedQty = remainingQty.min(lot.remaining);
                    if (matchedQty.compareTo(BigDecimal.ZERO) <= 0) {
                        buyLots.poll();   // defensive: zero/negative-quantity lot
                        continue;
                    }

                    BigDecimal costBasis = matchedQty.multiply(lot.txn.getPrice());
                    BigDecimal saleValue = matchedQty.multiply(sell.getPrice());
                    BigDecimal gain = saleValue.subtract(costBasis);

                    long holdingDays = ChronoUnit.DAYS.between(
                            lot.txn.getTransactionDate().toLocalDate(), sell.getTransactionDate().toLocalDate());

                    CapitalGain capitalGain = CapitalGain.builder()
                            .holdingId(holding.getId().toString())
                            .symbol(holding.getSymbol())
                            .assetType(holding.getAssetType())
                            .saleDate(sell.getTransactionDate().toLocalDate())
                            .purchaseDate(lot.txn.getTransactionDate().toLocalDate())
                            .quantity(matchedQty)
                            .salePrice(sell.getPrice())
                            .purchasePrice(lot.txn.getPrice())
                            .costBasis(costBasis)
                            .saleProceeds(saleValue)
                            .gain(gain)
                            .holdingDays(holdingDays)
                            .isLongTerm(ruleRegistry.forDate(sell.getTransactionDate().toLocalDate())
                                    .capitalGains().isLongTerm(holding.getAssetType(), holdingDays))
                            .build();

                    // Record every disposal, gain OR loss. Dropping losses here understated
                    // nothing visibly but silently inflated taxable gains, because losses
                    // are legally available for set-off.
                    switch (holding.getAssetType()) {
                        case EQUITY, ETF -> equityGains.add(capitalGain);
                        case MUTUAL_FUND -> {
                            if (isEquityOrientedMF(holding)) {
                                equityGains.add(capitalGain);
                            } else {
                                debtGains.add(capitalGain);
                            }
                        }
                        case GOLD, SGB -> goldGains.add(capitalGain);
                        case CRYPTO -> cryptoGains.add(capitalGain);
                        case REAL_ESTATE -> realEstateGains.add(capitalGain);
                        default -> debtGains.add(capitalGain);
                    }

                    remainingQty = remainingQty.subtract(matchedQty);
                    lot.remaining = lot.remaining.subtract(matchedQty);
                    if (lot.remaining.compareTo(BigDecimal.ZERO) <= 0) {
                        buyLots.poll();
                    }
                }

                if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                    // More sold than bought: the buy history is incomplete (e.g. holdings
                    // imported without their original purchases). Cost basis for the excess
                    // is unknowable, so it is excluded rather than guessed at zero.
                    log.warn("Unmatched sell quantity {} for holding {} ({}) on {} - buy history incomplete, "
                                    + "capital gains for this portion are excluded",
                            remainingQty, holding.getSymbol(), holding.getId(),
                            sell.getTransactionDate().toLocalDate());
                }
            }
        }

        BigDecimal grossEquityLTCG = sumGains(equityGains, true);
        BigDecimal grossEquitySTCG = sumGains(equityGains, false);
        BigDecimal equityLTCL = sumLosses(equityGains, true);
        BigDecimal equitySTCL = sumLosses(equityGains, false);

        // Rates can change mid-year (23 July 2024), so gains are bucketed by the rule period
        // in force on the sale date. Losses and the LTCG exemption are annual concepts, so
        // they are pooled across the year and then applied to the most heavily taxed buckets
        // first — the arrangement most favourable to the taxpayer.
        List<RateBucket> buckets = bucketByRatePeriod(equityGains);

        // Set-off: a short-term loss may be set off against short-term gains and long-term
        // gains; a long-term loss only against long-term gains.
        BigDecimal stclPool = equitySTCL;
        stclPool = applyAgainst(buckets, stclPool, true);    // STCG first, it is taxed higher
        BigDecimal stclAfterStcg = stclPool;
        stclPool = applyAgainst(buckets, stclPool, false);   // then spill onto LTCG
        applyAgainst(buckets, equityLTCL, false);            // LTCL: long-term only

        BigDecimal stcgAfterSetOff = buckets.stream()
                .map(b -> b.stcg).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ltcgAfterSetOff = buckets.stream()
                .map(b -> b.ltcg).reduce(BigDecimal.ZERO, BigDecimal::add);

        // The LTCG exemption is an annual allowance. Where the year spans several rate
        // periods, the allowance in force at year end is used and applied once.
        TaxRuleSet yearEndRules = ruleRegistry.forFinancialYear(financialYear);
        BigDecimal exemptionLimit = yearEndRules.capitalGains().ltcgExemption();
        applyExemption(buckets, exemptionLimit);

        BigDecimal taxableEquityLTCG = buckets.stream()
                .map(b -> b.ltcg).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxOnEquityLTCG = BigDecimal.ZERO;
        BigDecimal taxOnEquitySTCG = BigDecimal.ZERO;
        for (RateBucket b : buckets) {
            taxOnEquityLTCG = taxOnEquityLTCG.add(b.ltcg.multiply(b.rules.ltcgRate()));
            taxOnEquitySTCG = taxOnEquitySTCG.add(b.stcg.multiply(b.rules.stcgRate()));
        }
        taxOnEquityLTCG = taxOnEquityLTCG.setScale(2, RoundingMode.HALF_UP);
        taxOnEquitySTCG = taxOnEquitySTCG.setScale(2, RoundingMode.HALF_UP);

        // Section 115BBH: losses on virtual digital assets cannot be set off against any
        // income, nor carried forward. Only positive crypto gains are taxable.
        BigDecimal grossCryptoGains = sumGains(cryptoGains, true).add(sumGains(cryptoGains, false));
        BigDecimal cryptoLosses = sumLosses(cryptoGains, true).add(sumLosses(cryptoGains, false));
        BigDecimal taxOnCrypto = BigDecimal.ZERO;
        for (CapitalGain cg : cryptoGains) {
            if (cg.getGain().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal rate = ruleRegistry.forDate(cg.getSaleDate()).capitalGains().cryptoRate();
                taxOnCrypto = taxOnCrypto.add(cg.getGain().multiply(rate));
            }
        }
        taxOnCrypto = taxOnCrypto.setScale(2, RoundingMode.HALF_UP);

        // Cess applies to all of it. It previously skipped debt, gold and real estate because
        // no tax was computed for them.
        BigDecimal cessableTax = taxOnEquityLTCG.add(taxOnEquitySTCG).add(taxOnCrypto);

        Map<String, Object> equity = new LinkedHashMap<>();
        equity.put("ltcg", grossEquityLTCG);
        equity.put("stcg", grossEquitySTCG);
        equity.put("ltcl", equityLTCL);
        equity.put("stcl", equitySTCL);
        equity.put("ltcgAfterSetOff", ltcgAfterSetOff);
        equity.put("stcgAfterSetOff", stcgAfterSetOff);
        equity.put("taxableLTCG", taxableEquityLTCG);
        equity.put("ltcgExemptionApplied", exemptionLimit.min(ltcgAfterSetOff));
        equity.put("taxOnLTCG", taxOnEquityLTCG);
        equity.put("taxOnSTCG", taxOnEquitySTCG);
        equity.put("unabsorbedShortTermLoss", stclPool);
        equity.put("unabsorbedLongTermLoss", unabsorbed(buckets, equityLTCL, stclAfterStcg.subtract(stclPool)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("financialYear", ruleRegistry.canonicalise(financialYear));
        result.put("ratePeriods", buckets.stream().map(b -> Map.of(
                "effectiveFrom", b.rulePeriod.effectiveFrom().toString(),
                "effectiveTo", b.rulePeriod.effectiveTo().toString(),
                "ltcgRate", b.rules.ltcgRate(),
                "stcgRate", b.rules.stcgRate(),
                "note", b.rulePeriod.note())).toList());
        result.put("equity", equity);
        Map<String, Object> debt = assetClassSummary(debtGains);
        Map<String, Object> gold = assetClassSummary(goldGains);
        Map<String, Object> realEstate = assetClassSummary(realEstateGains);
        BigDecimal otherClassTax = ((BigDecimal) debt.get("tax"))
                .add((BigDecimal) gold.get("tax"))
                .add((BigDecimal) realEstate.get("tax"));

        result.put("debt", debt);
        result.put("gold", gold);
        result.put("crypto", Map.of(
                "gains", grossCryptoGains,
                "losses", cryptoLosses,
                "tax", taxOnCrypto,
                "lossSetOffAllowed", false));
        result.put("realEstate", realEstate);
        BigDecimal cess = cessableTax.add(otherClassTax)
                .multiply(yearEndRules.cessRate()).setScale(2, RoundingMode.HALF_UP);
        result.put("otherAssetTax", otherClassTax);
        result.put("totalTax", cessableTax.add(otherClassTax).add(cess));
        result.put("cess", cess);
        return result;
    }

    /** Equity gains grouped by the rate period in force on their sale date, highest rate first. */
    private List<RateBucket> bucketByRatePeriod(List<CapitalGain> gains) {
        Map<TaxRuleSet, RateBucket> byPeriod = new LinkedHashMap<>();
        for (CapitalGain cg : gains) {
            if (cg.getGain().compareTo(BigDecimal.ZERO) <= 0) {
                continue;   // losses are pooled annually, not bucketed
            }
            TaxRuleSet period = ruleRegistry.forDate(cg.getSaleDate());
            RateBucket bucket = byPeriod.computeIfAbsent(period, RateBucket::new);
            if (cg.isLongTerm()) {
                bucket.ltcg = bucket.ltcg.add(cg.getGain());
            } else {
                bucket.stcg = bucket.stcg.add(cg.getGain());
            }
        }
        List<RateBucket> buckets = new ArrayList<>(byPeriod.values());
        // Highest-taxed first, so pooled losses and the exemption relieve the dearest gains.
        buckets.sort(Comparator.comparing((RateBucket b) -> b.rules.stcgRate()).reversed());
        return buckets;
    }

    /** Draws {@code pool} down against bucket gains, returning what remains unabsorbed. */
    private BigDecimal applyAgainst(List<RateBucket> buckets, BigDecimal pool, boolean shortTerm) {
        BigDecimal remaining = pool;
        for (RateBucket b : buckets) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal available = shortTerm ? b.stcg : b.ltcg;
            BigDecimal used = available.min(remaining);
            if (shortTerm) {
                b.stcg = b.stcg.subtract(used);
            } else {
                b.ltcg = b.ltcg.subtract(used);
            }
            remaining = remaining.subtract(used);
        }
        return remaining;
    }

    /** Applies the annual LTCG exemption to the dearest long-term gains first. */
    private void applyExemption(List<RateBucket> buckets, BigDecimal exemption) {
        List<RateBucket> byLtcgRate = new ArrayList<>(buckets);
        byLtcgRate.sort(Comparator.comparing((RateBucket b) -> b.rules.ltcgRate()).reversed());
        applyAgainst(byLtcgRate, exemption, false);
    }

    private BigDecimal unabsorbed(List<RateBucket> buckets, BigDecimal ltcl, BigDecimal stclUsedAgainstLtcg) {
        BigDecimal ltcgAvailable = buckets.stream()
                .map(b -> b.ltcg).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Whatever long-term loss could not be absorbed by remaining long-term gains.
        return ltcl.subtract(ltcl.min(ltcgAvailable.add(stclUsedAgainstLtcg))).max(BigDecimal.ZERO);
    }

    /** Mutable per-rate-period accumulator used while applying set-off and the exemption. */
    private static final class RateBucket {
        private final TaxRuleSet rulePeriod;
        private final CapitalGainsRules rules;
        private BigDecimal ltcg = BigDecimal.ZERO;
        private BigDecimal stcg = BigDecimal.ZERO;

        RateBucket(TaxRuleSet rulePeriod) {
            this.rulePeriod = rulePeriod;
            this.rules = rulePeriod.capitalGains();
        }
    }

    /**
     * Gains, losses and tax for an asset class outside equity and crypto.
     *
     * <p>Long-term gold and property have a fixed rate, so tax is computed. Debt funds, and
     * short-term disposals of gold and property, fall into the taxpayer's income slab — which
     * depends on total income and regime, neither of which this calculator sees. Those are
     * reported with {@code taxAtSlabRate} true and no tax figure, rather than a guess.
     */
    private Map<String, Object> assetClassSummary(List<CapitalGain> gains) {
        BigDecimal longGains = sumGains(gains, true);
        BigDecimal shortGains = sumGains(gains, false);
        BigDecimal longLosses = sumLosses(gains, true);
        BigDecimal shortLosses = sumLosses(gains, false);

        BigDecimal longAfterSetOff = longGains.subtract(longLosses).max(BigDecimal.ZERO);
        BigDecimal shortAfterSetOff = shortGains.subtract(shortLosses).max(BigDecimal.ZERO);

        // Long-term tax at the period rate for each disposal; short-term is slab-rated.
        BigDecimal tax = BigDecimal.ZERO;
        boolean anySlabRated = false;
        for (CapitalGain cg : gains) {
            if (cg.getGain().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            CapitalGainsRules periodRules = ruleRegistry.forDate(cg.getSaleDate()).capitalGains();
            if (periodRules.isSlabRated(cg.getAssetType(), cg.isLongTerm())) {
                anySlabRated = true;
            } else {
                tax = tax.add(cg.getGain().multiply(periodRules.otherAssetLtcgRate()));
            }
        }
        // Losses relieve the gains they can, so scale the computed tax down proportionally
        // rather than taxing gross gains that a loss has already absorbed.
        if (longGains.compareTo(BigDecimal.ZERO) > 0 && longAfterSetOff.compareTo(longGains) < 0) {
            tax = tax.multiply(longAfterSetOff).divide(longGains, 10, RoundingMode.HALF_UP);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gains", longGains.add(shortGains));
        m.put("losses", longLosses.add(shortLosses));
        m.put("net", longGains.add(shortGains).subtract(longLosses).add(shortLosses.negate()));
        m.put("longTermGains", longGains);
        m.put("shortTermGains", shortGains);
        m.put("longTermAfterSetOff", longAfterSetOff);
        m.put("shortTermAfterSetOff", shortAfterSetOff);
        m.put("tax", tax.setScale(2, RoundingMode.HALF_UP));
        m.put("taxAtSlabRate", anySlabRated);
        if (anySlabRated) {
            m.put("slabRateNote", "Short-term gains here are taxed at your income slab rate, "
                    + "which depends on your total income and regime, so no figure is computed");
        }
        return m;
    }

    /** A buy transaction plus how much of it is still unmatched by later sells. */
    private static final class Lot {
        private final Transaction txn;
        private BigDecimal remaining;

        Lot(Transaction txn) {
            this.txn = txn;
            this.remaining = txn.getQuantity().abs();
        }
    }

    private boolean isEquityOrientedMF(Holding holding) {
        if (holding.getMetadata() == null) return true;
        Object equity = holding.getMetadata().get("equityOrientation");
        if (equity == null) return true;
        return Boolean.parseBoolean(equity.toString());
    }

    /** Sum of positive gains for the given term. */
    private BigDecimal sumGains(List<CapitalGain> gains, boolean longTerm) {
        return gains.stream()
                .filter(g -> g.isLongTerm() == longTerm)
                .map(CapitalGain::getGain)
                .filter(g -> g.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Sum of losses for the given term, returned as a positive magnitude (ITR reports losses unsigned). */
    private BigDecimal sumLosses(List<CapitalGain> gains, boolean longTerm) {
        return gains.stream()
                .filter(g -> g.isLongTerm() == longTerm)
                .map(CapitalGain::getGain)
                .filter(g -> g.compareTo(BigDecimal.ZERO) < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .abs();
    }

    @lombok.Builder
    @lombok.Getter
    public static class CapitalGain {
        private String holdingId;
        private String symbol;
        private AssetType assetType;
        private LocalDate saleDate;
        private LocalDate purchaseDate;
        private BigDecimal quantity;
        private BigDecimal salePrice;
        private BigDecimal purchasePrice;
        private BigDecimal costBasis;
        private BigDecimal saleProceeds;
        private BigDecimal gain;
        private long holdingDays;
        private boolean isLongTerm;
    }
}
