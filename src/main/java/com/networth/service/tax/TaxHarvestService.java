package com.networth.service.tax;

import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.service.tax.rules.CapitalGainsRules;
import com.networth.service.tax.rules.TaxRuleRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxHarvestService {

    private final HoldingRepository holdingRepository;
    /** Rates and the LTCG exemption come from here, so they cannot drift from the tax report. */
    private final TaxRuleRegistry ruleRegistry;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findHarvestingOpportunities(UUID userId, String financialYear) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        List<Map<String, Object>> opportunities = new ArrayList<>();

        CapitalGainsRules rules = ruleRegistry.forFinancialYear(financialYear).capitalGains();
        BigDecimal usedExemption = calculateUsedExemption(userId, financialYear);
        BigDecimal remainingExemption = rules.ltcgExemption().subtract(usedExemption);

        for (Holding holding : holdings) {
            if (holding.getAssetType() != AssetType.EQUITY
                    && holding.getAssetType() != AssetType.ETF) {
                continue;
            }

            if (holding.getCurrentPrice() == null || holding.getCurrentValue() == null) {
                continue;
            }

            BigDecimal costBasis = holding.getQuantity().multiply(holding.getAverageBuyPrice());
            BigDecimal unrealizedGain = holding.getCurrentValue().subtract(costBasis);

            if (unrealizedGain.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            long holdingDays = calculateHoldingDays(userId, holding.getId());
            boolean nearLTCGThreshold = holdingDays >= 330 && holdingDays < 365;

            Map<String, Object> opportunity = Map.of(
                    "holdingId", holding.getId(),
                    "symbol", holding.getSymbol(),
                    "unrealizedGain", unrealizedGain,
                    "currentPrice", holding.getCurrentPrice(),
                    "avgBuyPrice", holding.getAverageBuyPrice(),
                    "holdingDays", holdingDays,
                    "isLongTerm", holdingDays >= 365,
                    "nearLTCGThreshold", nearLTCGThreshold,
                    "taxSavings", calculateTaxSavings(unrealizedGain, holdingDays, remainingExemption, rules),
                    "action", nearLTCGThreshold ? "wait_for_ltcg" : "sell_and_rebuy"
            );

            opportunities.add(opportunity);
        }

        return opportunities;
    }

    private BigDecimal calculateUsedExemption(UUID userId, String financialYear) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        BigDecimal totalLTCG = BigDecimal.ZERO;

        for (Holding holding : holdings) {
            if (holding.getRealizedPnl() != null && holding.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0) {
                totalLTCG = totalLTCG.add(holding.getRealizedPnl());
            }
        }

        return totalLTCG;
    }

    private long calculateHoldingDays(UUID userId, UUID holdingId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        Holding holding = holdings.stream()
                .filter(h -> h.getId().equals(holdingId))
                .findFirst()
                .orElse(null);

        if (holding == null || holding.getCreatedAt() == null) {
            return 0;
        }

        return java.time.temporal.ChronoUnit.DAYS.between(
                holding.getCreatedAt().toLocalDate(), LocalDate.now());
    }

    /**
     * NOTE: the exemption handling here looks inverted — {@code gain.min(remainingExemption)}
     * is the portion *inside* the exemption, i.e. the part that is not taxed, and the
     * short-term branch ignores the exemption entirely. Behaviour is deliberately preserved
     * while rates are centralised; correcting it is tracked separately so that a rates change
     * and a logic change do not land in the same commit.
     */
    private BigDecimal calculateTaxSavings(BigDecimal gain, long holdingDays,
                                           BigDecimal remainingExemption, CapitalGainsRules rules) {
        if (gain.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal taxableGain = gain.min(remainingExemption);

        if (holdingDays >= rules.longTermThresholdFor(AssetType.EQUITY)) {
            return taxableGain.multiply(rules.ltcgRate());
        } else {
            return gain.multiply(rules.stcgRate());
        }
    }
}
