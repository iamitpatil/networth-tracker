package com.networth.service.portfolio;

import com.networth.model.entity.Holding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Keeps a holding's position — quantity and weighted-average buy price — in step with the
 * events recorded against it.
 *
 * <p>This is the <b>position</b> view, deliberately separate from the <b>lot</b> view in
 * {@code CapitalGainsCalculator}. The position answers "what do I own and what did it cost me
 * on average"; the lot view answers "which specific shares did I sell and when did I buy
 * them", which is what tax needs. A weighted average cannot answer the second question, and
 * replaying every lot is wasteful for the first, so both exist.
 *
 * <p>The invariant every method here preserves: <b>total cost = quantity × averageBuyPrice</b>.
 * Corporate actions move that total around without cash changing hands, so each one is a
 * specific, different rearrangement of the two factors:
 *
 * <table border="1">
 *   <caption>Effect of each action on the position</caption>
 *   <tr><th>Action</th><th>Quantity</th><th>Total cost</th><th>Average price</th></tr>
 *   <tr><td>Bonus</td><td>rises</td><td>unchanged</td><td>falls</td></tr>
 *   <tr><td>Split</td><td>rises</td><td>unchanged</td><td>falls proportionally</td></tr>
 *   <tr><td>Reverse split</td><td>falls</td><td>unchanged</td><td>rises proportionally</td></tr>
 *   <tr><td>Demerger (parent)</td><td>unchanged</td><td>falls</td><td>falls</td></tr>
 *   <tr><td>Demerger (resulting)</td><td>rises from 0</td><td>rises</td><td>the apportioned cost</td></tr>
 * </table>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CostBasisService {

    /** Scale for a stored average price, matching the price column's DECIMAL(18,4). */
    private static final int PRICE_SCALE = 4;

    public void updateBuy(Holding holding, BigDecimal quantity, BigDecimal price) {
        BigDecimal totalCost = totalCost(holding).add(price.multiply(quantity));
        BigDecimal totalQuantity = quantity(holding).add(quantity);

        holding.setQuantity(totalQuantity);
        holding.setAverageBuyPrice(averagePrice(totalCost, totalQuantity));
    }

    public void updateSell(Holding holding, BigDecimal quantity, BigDecimal sellPrice) {
        BigDecimal costBasis = averageBuyPrice(holding).multiply(quantity);
        BigDecimal saleValue = sellPrice.multiply(quantity);
        BigDecimal realizedGain = saleValue.subtract(costBasis);

        BigDecimal realized = holding.getRealizedPnl() == null ? BigDecimal.ZERO : holding.getRealizedPnl();
        holding.setRealizedPnl(realized.add(realizedGain));
        holding.setQuantity(quantity(holding).subtract(quantity));
        // Average price is left alone: selling part of a position does not change what the
        // remaining shares cost.
    }

    /**
     * Bonus shares: quantity rises, total cost does not.
     *
     * <p>The average price therefore falls, which is the whole reason a bonus issue looks like
     * a price drop without being a loss. Treating the new shares as a buy at the market price
     * would inflate the cost basis and understate the eventual gain.
     */
    public void applyBonus(Holding holding, BigDecimal bonusQuantity) {
        if (bonusQuantity == null || bonusQuantity.signum() <= 0) {
            return;
        }
        BigDecimal totalCost = totalCost(holding);          // unchanged: bonus shares are free
        BigDecimal totalQuantity = quantity(holding).add(bonusQuantity);

        holding.setQuantity(totalQuantity);
        holding.setAverageBuyPrice(averagePrice(totalCost, totalQuantity));
    }

    /**
     * Split or consolidation.
     *
     * @param costFactor multiplier on the per-share cost: 0.5 for a 1:2 split (one share
     *                   becomes two), 2 for a 2:1 consolidation. Quantity moves inversely, so
     *                   the total cost is untouched.
     */
    public void applySplit(Holding holding, BigDecimal costFactor) {
        if (costFactor == null || costFactor.signum() <= 0) {
            log.warn("Ignoring split on holding {} with unusable factor {}", holding.getId(), costFactor);
            return;
        }
        BigDecimal newQuantity = quantity(holding).divide(costFactor, 8, RoundingMode.HALF_UP);
        holding.setQuantity(newQuantity);
        holding.setAverageBuyPrice(averageBuyPrice(holding).multiply(costFactor)
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * The parent side of a demerger: part of the cost moves to the resulting company under
     * s.49(2C), while the share count stays exactly as it was.
     *
     * @param retainedFraction share of the original cost the parent keeps — 0.85 where 15% is
     *                         apportioned away
     */
    public void applyDemergerOut(Holding holding, BigDecimal retainedFraction) {
        if (retainedFraction == null || retainedFraction.signum() < 0
                || retainedFraction.compareTo(BigDecimal.ONE) > 0) {
            log.warn("Ignoring demerger on holding {} with unusable retained fraction {}",
                    holding.getId(), retainedFraction);
            return;
        }
        holding.setAverageBuyPrice(averageBuyPrice(holding).multiply(retainedFraction)
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        // Quantity untouched on purpose: a demerger gives you shares in a new company, it does
        // not take away the ones you hold.
    }

    // ── null-tolerant accessors ───────────────────────────────────────
    // Imported and broker-synced holdings can arrive with either field null. Reading them
    // directly threw NullPointerException from inside a transaction, losing the whole write.

    private static BigDecimal quantity(Holding holding) {
        return holding.getQuantity() == null ? BigDecimal.ZERO : holding.getQuantity();
    }

    private static BigDecimal averageBuyPrice(Holding holding) {
        return holding.getAverageBuyPrice() == null ? BigDecimal.ZERO : holding.getAverageBuyPrice();
    }

    private static BigDecimal totalCost(Holding holding) {
        return averageBuyPrice(holding).multiply(quantity(holding));
    }

    private static BigDecimal averagePrice(BigDecimal totalCost, BigDecimal totalQuantity) {
        if (totalQuantity.signum() <= 0) {
            return BigDecimal.ZERO;   // nothing held; a per-share cost is meaningless
        }
        return totalCost.divide(totalQuantity, PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
