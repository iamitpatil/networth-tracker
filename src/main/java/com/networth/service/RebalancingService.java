package com.networth.service;

import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RebalancingService {

    private final HoldingRepository holdingRepository;

    private static final Map<String, Map<AssetType, Double>> DEFAULT_ALLOCATIONS = Map.of(
            "conservative", Map.of(
                    AssetType.EQUITY, 30.0,
                    AssetType.MUTUAL_FUND, 20.0,
                    AssetType.FD, 25.0,
                    AssetType.BOND, 10.0,
                    AssetType.GOLD, 10.0,
                    AssetType.CASH, 5.0
            ),
            "moderate", Map.of(
                    AssetType.EQUITY, 45.0,
                    AssetType.MUTUAL_FUND, 20.0,
                    AssetType.FD, 10.0,
                    AssetType.BOND, 10.0,
                    AssetType.GOLD, 10.0,
                    AssetType.CASH, 5.0
            ),
            "aggressive", Map.of(
                    AssetType.EQUITY, 55.0,
                    AssetType.MUTUAL_FUND, 20.0,
                    AssetType.FD, 5.0,
                    AssetType.BOND, 5.0,
                    AssetType.GOLD, 10.0,
                    AssetType.CASH, 5.0
            )
    );

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRebalancingSuggestions(UUID userId, String riskProfile) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        Map<AssetType, Double> targetAllocation = DEFAULT_ALLOCATIONS.getOrDefault(riskProfile, DEFAULT_ALLOCATIONS.get("moderate"));

        BigDecimal totalValue = holdings.stream()
                .map(h -> h.getCurrentValue() != null ? h.getCurrentValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        List<Map<String, Object>> suggestions = new ArrayList<>();

        for (Map.Entry<AssetType, Double> entry : targetAllocation.entrySet()) {
            AssetType assetType = entry.getKey();
            double targetPercentage = entry.getValue();

            BigDecimal currentValue = holdings.stream()
                    .filter(h -> h.getAssetType() == assetType)
                    .map(h -> h.getCurrentValue() != null ? h.getCurrentValue() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal targetValue = totalValue.multiply(BigDecimal.valueOf(targetPercentage / 100));
            BigDecimal deviation = currentValue.subtract(targetValue);
            BigDecimal deviationPercentage = deviation.divide(targetValue.max(BigDecimal.ONE), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

            boolean needsRebalancing = Math.abs(deviationPercentage.doubleValue()) > 5.0;

            suggestions.add(Map.of(
                    "assetType", assetType.toString(),
                    "targetPercentage", BigDecimal.valueOf(targetPercentage).setScale(2, RoundingMode.HALF_UP),
                    "currentPercentage", currentValue.divide(totalValue, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
                    "targetValue", targetValue.setScale(2, RoundingMode.HALF_UP),
                    "currentValue", currentValue.setScale(2, RoundingMode.HALF_UP),
                    "deviation", deviation.setScale(2, RoundingMode.HALF_UP),
                    "deviationPercentage", deviationPercentage,
                    "action", needsRebalancing ? (deviation.compareTo(BigDecimal.ZERO) > 0 ? "sell" : "buy") : "hold",
                    "needsRebalancing", needsRebalancing
            ));
        }

        return suggestions;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAllocationDrift(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);

        BigDecimal totalValue = holdings.stream()
                .map(h -> h.getCurrentValue() != null ? h.getCurrentValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<AssetType, BigDecimal> currentAllocation = new EnumMap<>(AssetType.class);
        for (Holding holding : holdings) {
            BigDecimal value = holding.getCurrentValue() != null ? holding.getCurrentValue() : BigDecimal.ZERO;
            currentAllocation.merge(holding.getAssetType(), value, BigDecimal::add);
        }

        Map<String, Object> drift = new LinkedHashMap<>();
        for (Map.Entry<AssetType, BigDecimal> entry : currentAllocation.entrySet()) {
            BigDecimal percentage = entry.getValue().divide(totalValue.max(BigDecimal.ONE), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            drift.put(entry.getKey().toString(), percentage.setScale(2, RoundingMode.HALF_UP));
        }

        return Map.of(
                "currentAllocation", drift,
                "totalValue", totalValue,
                "timestamp", java.time.LocalDateTime.now()
        );
    }
}
