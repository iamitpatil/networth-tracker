package com.networth.service.tax;

import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.TransactionRepository;
import com.networth.service.tax.rules.CapitalGainsRules;
import com.networth.service.tax.rules.TaxRuleRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Finds year-end tax actions worth taking on equity and ETF holdings.
 *
 * <p>Two distinct strategies are surfaced, each tagged with a {@code type} so the caller can
 * label them correctly. They pull in opposite directions and were previously conflated — the
 * UI described one and the backend computed the other:
 *
 * <ul>
 *   <li>{@code LOSS_HARVEST} — sell a holding that is down, realising a loss that offsets
 *       gains this year and carries forward eight years. A short-term loss is worth more
 *       because it can relieve short-term gains, taxed at the higher rate.</li>
 *   <li>{@code GAIN_HARVEST} — sell a long-term holding whose gain fits inside the remaining
 *       annual LTCG exemption, so the gain is booked tax-free and the cost basis resets
 *       upward. Only useful while exemption headroom remains.</li>
 *   <li>{@code WAIT_FOR_LTCG} — an advisory, not an action: the holding is close to the
 *       long-term threshold, so selling now would attract the higher short-term rate.</li>
 * </ul>
 *
 * <p>Rates and the exemption come from {@link TaxRuleRegistry}, and exemption already consumed
 * is read from {@link CapitalGainsCalculator} rather than re-derived, so this service cannot
 * disagree with the tax report.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaxHarvestService {

    /** Days before the long-term threshold within which we advise waiting rather than selling. */
    private static final long NEAR_LTCG_WINDOW_DAYS = 35;

    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final TaxRuleRegistry ruleRegistry;
    private final CapitalGainsCalculator capitalGainsCalculator;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findHarvestingOpportunities(UUID userId, String financialYear) {
        CapitalGainsRules rules = ruleRegistry.forFinancialYear(financialYear).capitalGains();

        // Two queries for the whole method. Previously the holding list was re-fetched once
        // per holding to find its purchase date, plus once more for the exemption.
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        Map<UUID, LocalDate> earliestBuy = earliestBuyDates(userId);

        BigDecimal remainingExemption = rules.ltcgExemption()
                .subtract(exemptionAlreadyUsed(userId, financialYear))
                .max(BigDecimal.ZERO);

        List<Map<String, Object>> opportunities = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Holding holding : holdings) {
            if (holding.getAssetType() != AssetType.EQUITY && holding.getAssetType() != AssetType.ETF) {
                continue;
            }
            if (holding.getCurrentValue() == null || holding.getQuantity() == null
                    || holding.getAverageBuyPrice() == null) {
                continue;
            }

            BigDecimal costBasis = holding.getQuantity().multiply(holding.getAverageBuyPrice());
            BigDecimal unrealised = holding.getCurrentValue().subtract(costBasis);
            if (unrealised.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            long holdingDays = holdingDays(holding, earliestBuy, today);
            long threshold = rules.longTermThresholdFor(holding.getAssetType());
            boolean isLongTerm = holdingDays >= threshold;

            if (unrealised.compareTo(BigDecimal.ZERO) < 0) {
                opportunities.add(lossHarvest(holding, unrealised.abs(), holdingDays, isLongTerm, rules));
            } else if (isLongTerm) {
                Map<String, Object> gainOpportunity =
                        gainHarvest(holding, unrealised, holdingDays, remainingExemption, rules);
                if (gainOpportunity != null) {
                    opportunities.add(gainOpportunity);
                    // Headroom is finite: once earmarked for one holding it is not available
                    // to the next, otherwise every holding would claim the same exemption.
                    BigDecimal claimed = (BigDecimal) gainOpportunity.get("exemptionUsed");
                    remainingExemption = remainingExemption.subtract(claimed).max(BigDecimal.ZERO);
                }
            } else if (threshold - holdingDays <= NEAR_LTCG_WINDOW_DAYS) {
                opportunities.add(waitForLongTerm(holding, unrealised, holdingDays, threshold, rules));
            }
        }

        return opportunities;
    }

    // ── the three opportunity kinds ───────────────────────────────────

    private Map<String, Object> lossHarvest(Holding holding, BigDecimal loss, long holdingDays,
                                            boolean isLongTerm, CapitalGainsRules rules) {
        // A short-term loss can relieve short-term gains, which are taxed higher, so it is
        // worth more than a long-term loss of the same size.
        BigDecimal reliefRate = isLongTerm ? rules.ltcgRate() : rules.stcgRate();
        BigDecimal savings = loss.multiply(reliefRate).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> m = base(holding, holdingDays, isLongTerm);
        m.put("type", "LOSS_HARVEST");
        m.put("unrealizedGain", loss.negate());
        m.put("currentLoss", loss);
        m.put("potentialSavings", savings);
        m.put("taxSavings", savings);
        m.put("exemptionUsed", BigDecimal.ZERO);
        m.put("action", "sell_to_book_loss");
        m.put("reason", String.format(
                "Booking this %s loss offsets gains at %s and carries forward for 8 years",
                isLongTerm ? "long-term" : "short-term", percent(reliefRate)));
        return m;
    }

    /** @return null when there is no exemption headroom left, so nothing to harvest. */
    private Map<String, Object> gainHarvest(Holding holding, BigDecimal gain, long holdingDays,
                                            BigDecimal remainingExemption, CapitalGainsRules rules) {
        if (remainingExemption.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal shelterable = gain.min(remainingExemption);
        BigDecimal savings = shelterable.multiply(rules.ltcgRate()).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> m = base(holding, holdingDays, true);
        m.put("type", "GAIN_HARVEST");
        m.put("unrealizedGain", gain);
        m.put("currentLoss", BigDecimal.ZERO);
        m.put("potentialSavings", savings);
        m.put("taxSavings", savings);
        m.put("exemptionUsed", shelterable);
        m.put("action", "sell_and_rebuy");
        m.put("reason", String.format(
                "%s of this gain fits the remaining LTCG exemption, so booking it now is tax-free "
                        + "and resets your cost basis", format(shelterable)));
        return m;
    }

    private Map<String, Object> waitForLongTerm(Holding holding, BigDecimal gain, long holdingDays,
                                                long threshold, CapitalGainsRules rules) {
        // Selling now realises a short-term gain, which costs tax rather than saving it.
        BigDecimal taxIfSoldNow = gain.multiply(rules.stcgRate()).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> m = base(holding, holdingDays, false);
        m.put("type", "WAIT_FOR_LTCG");
        m.put("unrealizedGain", gain);
        m.put("currentLoss", BigDecimal.ZERO);
        // No saving is available here. Reporting the avoidable tax as a "saving" is what the
        // previous implementation did, which inverted the advice.
        m.put("potentialSavings", BigDecimal.ZERO);
        m.put("taxSavings", BigDecimal.ZERO);
        m.put("taxIfSoldNow", taxIfSoldNow);
        m.put("exemptionUsed", BigDecimal.ZERO);
        m.put("action", "wait_for_ltcg");
        m.put("reason", String.format(
                "%d more days to long-term treatment; selling now would cost %s in short-term tax",
                threshold - holdingDays, format(taxIfSoldNow)));
        return m;
    }

    private Map<String, Object> base(Holding holding, long holdingDays, boolean isLongTerm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("holdingId", holding.getId());
        m.put("symbol", holding.getSymbol());
        m.put("quantity", holding.getQuantity());
        m.put("currentPrice", holding.getCurrentPrice());
        m.put("avgBuyPrice", holding.getAverageBuyPrice());
        m.put("holdingDays", holdingDays);
        m.put("isLongTerm", isLongTerm);
        return m;
    }

    // ── inputs ────────────────────────────────────────────────────────

    /**
     * Exemption already consumed this year, taken from the capital gains report rather than
     * recomputed.
     *
     * <p>The previous version summed lifetime {@code realizedPnl} across every holding,
     * ignoring both the financial year it was given and whether the gain was long-term, so
     * the headroom it reported bore little relation to the actual exemption used.
     */
    private BigDecimal exemptionAlreadyUsed(UUID userId, String financialYear) {
        try {
            Map<String, Object> report = capitalGainsCalculator.calculateCapitalGains(userId, financialYear);
            @SuppressWarnings("unchecked")
            Map<String, Object> equity = (Map<String, Object>) report.get("equity");
            Object applied = equity == null ? null : equity.get("ltcgExemptionApplied");
            return applied instanceof BigDecimal value ? value : BigDecimal.ZERO;
        } catch (RuntimeException e) {
            // Never block the suggestions on a reporting failure; assume no headroom used and
            // log, rather than silently claiming the full exemption is available.
            log.warn("Could not determine exemption already used for {} in {}: {}",
                    userId, financialYear, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** Earliest acquisition date per holding, from one query over the user's transactions. */
    private Map<UUID, LocalDate> earliestBuyDates(UUID userId) {
        return transactionRepository.findByUserId(userId).stream()
                .filter(t -> t.getHoldingId() != null && t.getTransactionDate() != null)
                // Every row that starts a lot, not just purchases. A holding received in a
                // demerger has no BUY at all: its only acquisition is the DEMERGER_IN, so
                // filtering to purchases left it with no date and fell back to the row's
                // creation timestamp, reporting a decade-old position as bought today.
                .filter(t -> t.getTransactionType().isAcquisition())
                .collect(Collectors.toMap(
                        Transaction::getHoldingId,
                        // acquisitionDate where present: demerged shares inherit the period the
                        // original shares were held (s.2(42A)), so they can already be long-term.
                        t -> (t.getAcquisitionDate() != null ? t.getAcquisitionDate() : t.getTransactionDate())
                                .toLocalDate(),
                        (a, b) -> a.isBefore(b) ? a : b));
    }

    /**
     * Days held, measured from the earliest purchase.
     *
     * <p>This used to measure from the holding row's {@code createdAt}, which is when the row
     * was written, not when the asset was bought. An imported holding purchased years ago was
     * therefore treated as bought today and misclassified as short-term.
     */
    private long holdingDays(Holding holding, Map<UUID, LocalDate> earliestBuy, LocalDate today) {
        LocalDate from = earliestBuy.get(holding.getId());
        if (from == null) {
            // No purchase transaction recorded. Fall back to the row date, which is the best
            // available signal, and say so.
            if (holding.getCreatedAt() == null) {
                return 0;
            }
            log.debug("Holding {} ({}) has no purchase transaction; using createdAt for holding period",
                    holding.getId(), holding.getSymbol());
            from = holding.getCreatedAt().toLocalDate();
        }
        return Math.max(0, ChronoUnit.DAYS.between(from, today));
    }

    private static String format(BigDecimal amount) {
        return "Rs. " + amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /** A rate fraction as a display percentage: 0.20 -> "20%", 0.125 -> "12.5%". */
    private static String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }
}
