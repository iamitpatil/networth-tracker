package com.networth.service.tax;

import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
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

    private static final BigDecimal EXEMPTION_LIMIT = new BigDecimal("125000");

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findHarvestingOpportunities(UUID userId, String financialYear) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        List<Map<String, Object>> opportunities = new ArrayList<>();

        BigDecimal usedExemption = calculateUsedExemption(userId, financialYear);
        BigDecimal remainingExemption = EXEMPTION_LIMIT.subtract(usedExemption);

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
                    "taxSavings", calculateTaxSavings(unrealizedGain, holdingDays, remainingExemption),
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

    private BigDecimal calculateTaxSavings(BigDecimal gain, long holdingDays, BigDecimal remainingExemption) {
        if (gain.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal taxableGain = gain.min(remainingExemption);

        if (holdingDays >= 365) {
            return taxableGain.multiply(new BigDecimal("0.125"));
        } else {
            return gain.multiply(new BigDecimal("0.20"));
        }
    }
}
