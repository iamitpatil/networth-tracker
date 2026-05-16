package com.networth.service;

import com.networth.model.entity.Holding;
import com.networth.model.entity.Liability;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.LiabilityRepository;
import com.networth.repository.SalaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HealthScoreService {

    private final HoldingRepository holdingRepository;
    private final LiabilityRepository liabilityRepository;
    private final SalaryRepository salaryRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> calculateHealthScore(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        List<Liability> liabilities = liabilityRepository.findByUserId(userId);

        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal liquidAssets = BigDecimal.ZERO;
        BigDecimal monthlyIncome = salaryRepository.findByUserIdOrderByPayDateDesc(userId).stream()
                .filter(s -> s.getPayDate() != null)
                .filter(s -> s.getPayDate().getMonthValue() == LocalDate.now().getMonthValue()
                        && s.getPayDate().getYear() == LocalDate.now().getYear())
                .map(s -> s.getAmount() != null ? s.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (Holding holding : holdings) {
            BigDecimal value = holding.getCurrentValue() != null ? holding.getCurrentValue() : BigDecimal.ZERO;
            totalAssets = totalAssets.add(value);
            if (isLiquid(holding.getAssetType())) {
                liquidAssets = liquidAssets.add(value);
            }
        }

        BigDecimal totalLiabilities = liabilities.stream()
                .map(Liability::getOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyEMI = liabilities.stream()
                .map(Liability::getMonthlyEmi)
                .filter(e -> e != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int emergencyMonths = monthlyEMI.compareTo(BigDecimal.ZERO) > 0
                ? liquidAssets.divide(monthlyEMI, 0, RoundingMode.DOWN).intValue()
                : 999;

        double debtToAssetRatio = totalAssets.compareTo(BigDecimal.ZERO) > 0
                ? totalLiabilities.divide(totalAssets, 4, RoundingMode.HALF_UP).doubleValue()
                : 0;

        int emergencyScore = calculateEmergencyScore(emergencyMonths);
        int diversificationScore = calculateDiversificationScore(holdings, totalAssets);
        int debtScore = calculateDebtScore(debtToAssetRatio);
        int liquidityScore = calculateLiquidityScore(liquidAssets, totalAssets);

        int totalScore = (emergencyScore + diversificationScore + debtScore + liquidityScore) / 4;

        return Map.of(
                "totalScore", totalScore,
                "grade", getGrade(totalScore),
                "breakdown", Map.of(
                        "emergencyFund", Map.of(
                                "score", emergencyScore,
                                "monthsCovered", emergencyMonths,
                                "status", getStatus(emergencyMonths, 6, 3)
                        ),
                        "diversification", Map.of(
                                "score", diversificationScore,
                                "assetClasses", countAssetClasses(holdings),
                                "status", getStatus(countAssetClasses(holdings), 5, 3)
                        ),
                        "debtRatio", Map.of(
                                "score", debtScore,
                                "ratio", BigDecimal.valueOf(debtToAssetRatio).setScale(2, RoundingMode.HALF_UP),
                                "status", debtToAssetRatio < 0.3 ? "Healthy" : debtToAssetRatio < 0.5 ? "Moderate" : "High"
                        ),
                        "liquidity", Map.of(
                                "score", liquidityScore,
                                "liquidPercentage", liquidAssets.divide(totalAssets.max(BigDecimal.ONE), 2, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
                                "status", liquidAssets.divide(totalAssets.max(BigDecimal.ONE), 2, RoundingMode.HALF_UP)
                                        .compareTo(BigDecimal.valueOf(0.3)) >= 0 ? "Healthy" : "Low"
                        )
                ),
                "totalAssets", totalAssets,
                "totalLiabilities", totalLiabilities,
                "netWorth", totalAssets.subtract(totalLiabilities)
        );
    }

    private int calculateEmergencyScore(int months) {
        if (months >= 6) return 100;
        if (months >= 3) return 70;
        if (months >= 1) return 40;
        return 10;
    }

    private int calculateDiversificationScore(List<Holding> holdings, BigDecimal totalAssets) {
        int assetClasses = countAssetClasses(holdings);
        if (assetClasses >= 5) return 100;
        if (assetClasses >= 3) return 70;
        if (assetClasses >= 2) return 50;
        return 25;
    }

    private int calculateDebtScore(double ratio) {
        if (ratio < 0.2) return 100;
        if (ratio < 0.4) return 70;
        if (ratio < 0.6) return 40;
        return 15;
    }

    private int calculateLiquidityScore(BigDecimal liquid, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) return 50;
        double ratio = liquid.divide(total, 4, RoundingMode.HALF_UP).doubleValue();
        if (ratio >= 0.4) return 100;
        if (ratio >= 0.25) return 70;
        if (ratio >= 0.10) return 40;
        return 15;
    }

    private int countAssetClasses(List<Holding> holdings) {
        return (int) holdings.stream()
                .map(Holding::getAssetType)
                .distinct()
                .count();
    }

    private boolean isLiquid(AssetType type) {
        return switch (type) {
            case EQUITY, ETF, MUTUAL_FUND, CASH, FD, BOND, CRYPTO, EPF, PPF, NPS -> true;
            default -> false;
        };
    }

    private String getStatus(int value, int good, int moderate) {
        if (value >= good) return "Healthy";
        if (value >= moderate) return "Moderate";
        return "Critical";
    }

    private String getGrade(int score) {
        if (score >= 90) return "A+";
        if (score >= 80) return "A";
        if (score >= 70) return "B+";
        if (score >= 60) return "B";
        if (score >= 50) return "C";
        return "D";
    }
}
