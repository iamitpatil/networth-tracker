package com.networth.service.portfolio;

import com.networth.model.dto.PortfolioSummary;
import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PortfolioSummaryService {

    private final HoldingRepository holdingRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "portfolioSummary", key = "#userId")
    public PortfolioSummary getSummary(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;
        BigDecimal totalRealizedPnl = BigDecimal.ZERO;

        Map<String, BigDecimal> assetAllocation = new HashMap<>();

        for (Holding holding : holdings) {
            BigDecimal invested = holding.getQuantity().multiply(holding.getAverageBuyPrice());
            totalInvested = totalInvested.add(invested);

            if (holding.getCurrentValue() != null) {
                currentValue = currentValue.add(holding.getCurrentValue());
            }

            totalRealizedPnl = totalRealizedPnl.add(holding.getRealizedPnl());

            String assetKey = holding.getAssetType().toString();
            assetAllocation.merge(assetKey, holding.getCurrentValue() != null
                    ? holding.getCurrentValue() : BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal unrealizedPnl = currentValue.subtract(totalInvested);
        BigDecimal totalPnl = totalRealizedPnl.add(unrealizedPnl);

        BigDecimal absoluteReturn = totalInvested.compareTo(BigDecimal.ZERO) > 0
                ? totalPnl.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;

        return PortfolioSummary.builder()
                .totalInvested(totalInvested)
                .currentValue(currentValue)
                .totalPnl(totalPnl)
                .realizedPnl(totalRealizedPnl)
                .unrealizedPnl(unrealizedPnl)
                .absoluteReturn(absoluteReturn)
                .assetAllocation(assetAllocation)
                .totalHoldings(holdings.size())
                .build();
    }
}
