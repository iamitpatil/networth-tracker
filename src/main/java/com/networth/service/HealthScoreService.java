package com.networth.service;

import com.networth.model.entity.Goal;
import com.networth.model.entity.Holding;
import com.networth.model.entity.Liability;
import com.networth.model.entity.Salary;
import com.networth.model.enums.AssetType;
import com.networth.repository.GoalRepository;
import com.networth.repository.HoldingRepository;
import com.networth.repository.LiabilityRepository;
import com.networth.repository.SalaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Financial health score based on 6 weighted dimensions:
 *
 *   1. Emergency Fund (20%)  — liquid assets vs monthly expenses
 *   2. Debt Health    (20%)  — EMI-to-income ratio + debt-to-asset ratio
 *   3. Savings Rate   (15%)  — monthly investment vs income
 *   4. Diversification(15%)  — asset class spread + concentration risk
 *   5. Liquidity      (15%)  — truly liquid assets vs total
 *   6. Goal Progress  (15%)  — on-track to meet financial goals
 */
@Service
@RequiredArgsConstructor
public class HealthScoreService {

    private final HoldingRepository holdingRepository;
    private final LiabilityRepository liabilityRepository;
    private final SalaryRepository salaryRepository;
    private final GoalRepository goalRepository;

    // Weights for each dimension (must sum to 100)
    private static final int W_EMERGENCY = 20;
    private static final int W_DEBT = 20;
    private static final int W_SAVINGS = 15;
    private static final int W_DIVERSIFICATION = 15;
    private static final int W_LIQUIDITY = 15;
    private static final int W_GOALS = 15;

    @Transactional(readOnly = true)
    public Map<String, Object> calculateHealthScore(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        List<Liability> liabilities = liabilityRepository.findByUserId(userId);
        List<Goal> goals = goalRepository.findByUserId(userId);

        // --- Compute base metrics ---
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal liquidAssets = BigDecimal.ZERO;
        Map<AssetType, BigDecimal> assetBreakdown = new EnumMap<>(AssetType.class);

        for (Holding h : holdings) {
            BigDecimal value = h.getCurrentValue() != null ? h.getCurrentValue() : BigDecimal.ZERO;
            totalAssets = totalAssets.add(value);
            assetBreakdown.merge(h.getAssetType(), value, BigDecimal::add);
            if (isTrulyLiquid(h.getAssetType())) {
                liquidAssets = liquidAssets.add(value);
            }
        }

        BigDecimal totalLiabilities = liabilities.stream()
                .map(l -> l.getOutstandingAmount() != null ? l.getOutstandingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyEMI = liabilities.stream()
                .map(Liability::getMonthlyEmi)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyIncome = getLatestMonthlyIncome(userId);

        // --- Score each dimension ---
        int emergencyScore = scoreEmergencyFund(liquidAssets, monthlyEMI, monthlyIncome);
        int debtScore = scoreDebtHealth(totalLiabilities, totalAssets, monthlyEMI, monthlyIncome);
        int savingsScore = scoreSavingsRate(totalAssets, monthlyIncome, holdings);
        int diversificationScore = scoreDiversification(assetBreakdown, totalAssets);
        int liquidityScore = scoreLiquidity(liquidAssets, totalAssets);
        int goalScore = scoreGoalProgress(goals);

        // --- Weighted total ---
        int totalScore = (emergencyScore * W_EMERGENCY
                + debtScore * W_DEBT
                + savingsScore * W_SAVINGS
                + diversificationScore * W_DIVERSIFICATION
                + liquidityScore * W_LIQUIDITY
                + goalScore * W_GOALS) / 100;

        // --- Recommendations ---
        List<String> recommendations = buildRecommendations(
                emergencyScore, debtScore, savingsScore, diversificationScore, liquidityScore, goalScore,
                liquidAssets, monthlyEMI, monthlyIncome, totalLiabilities, totalAssets, goals, assetBreakdown);

        // Emergency months for display
        BigDecimal monthlyExpenses = monthlyEMI.add(monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                ? monthlyIncome.multiply(BigDecimal.valueOf(0.5)) : BigDecimal.valueOf(25000));
        int emergencyMonths = monthlyExpenses.compareTo(BigDecimal.ZERO) > 0
                ? liquidAssets.divide(monthlyExpenses, 0, RoundingMode.DOWN).intValue() : 999;

        double debtToAssetRatio = totalAssets.compareTo(BigDecimal.ZERO) > 0
                ? totalLiabilities.divide(totalAssets, 4, RoundingMode.HALF_UP).doubleValue() : 0;

        double emiToIncomeRatio = monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                ? monthlyEMI.divide(monthlyIncome, 4, RoundingMode.HALF_UP).doubleValue() : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalScore", totalScore);
        result.put("grade", getGrade(totalScore));
        result.put("breakdown", Map.of(
                "emergencyFund", Map.of(
                        "score", emergencyScore,
                        "monthsCovered", Math.min(emergencyMonths, 99),
                        "status", getStatusFromScore(emergencyScore),
                        "weight", W_EMERGENCY
                ),
                "debtHealth", Map.of(
                        "score", debtScore,
                        "debtToAssetRatio", BigDecimal.valueOf(debtToAssetRatio).setScale(2, RoundingMode.HALF_UP),
                        "emiToIncomeRatio", BigDecimal.valueOf(emiToIncomeRatio).setScale(2, RoundingMode.HALF_UP),
                        "status", getStatusFromScore(debtScore),
                        "weight", W_DEBT
                ),
                "savingsRate", Map.of(
                        "score", savingsScore,
                        "status", getStatusFromScore(savingsScore),
                        "weight", W_SAVINGS
                ),
                "diversification", Map.of(
                        "score", diversificationScore,
                        "assetClasses", countAssetClasses(holdings),
                        "status", getStatusFromScore(diversificationScore),
                        "weight", W_DIVERSIFICATION
                ),
                "liquidity", Map.of(
                        "score", liquidityScore,
                        "liquidPercentage", totalAssets.compareTo(BigDecimal.ZERO) > 0
                                ? liquidAssets.divide(totalAssets, 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO,
                        "status", getStatusFromScore(liquidityScore),
                        "weight", W_LIQUIDITY
                ),
                "goalProgress", Map.of(
                        "score", goalScore,
                        "totalGoals", goals.size(),
                        "status", getStatusFromScore(goalScore),
                        "weight", W_GOALS
                )
        ));
        result.put("recommendations", recommendations);
        result.put("totalAssets", totalAssets);
        result.put("totalLiabilities", totalLiabilities);
        result.put("netWorth", totalAssets.subtract(totalLiabilities));
        return result;
    }

    // --- Dimension 1: Emergency Fund (20%) ---
    // How many months of expenses can liquid assets cover?
    private int scoreEmergencyFund(BigDecimal liquidAssets, BigDecimal monthlyEMI, BigDecimal monthlyIncome) {
        // Estimate monthly expenses = EMIs + 50% of income (living costs) or 25K minimum
        BigDecimal estimatedMonthlyExpense = monthlyEMI.add(
                monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                        ? monthlyIncome.multiply(BigDecimal.valueOf(0.5))
                        : BigDecimal.valueOf(25000));

        if (estimatedMonthlyExpense.compareTo(BigDecimal.ZERO) <= 0) return 80;

        int months = liquidAssets.divide(estimatedMonthlyExpense, 0, RoundingMode.DOWN).intValue();
        if (months >= 12) return 100;
        if (months >= 6) return 85;
        if (months >= 3) return 65;
        if (months >= 1) return 40;
        return 10;
    }

    // --- Dimension 2: Debt Health (20%) ---
    // Combines EMI-to-income ratio and debt-to-asset ratio
    private int scoreDebtHealth(BigDecimal totalDebt, BigDecimal totalAssets,
                                BigDecimal monthlyEMI, BigDecimal monthlyIncome) {
        // No debt = perfect score
        if (totalDebt.compareTo(BigDecimal.ZERO) <= 0) return 100;

        int emiScore = 100;
        if (monthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
            double emiRatio = monthlyEMI.divide(monthlyIncome, 4, RoundingMode.HALF_UP).doubleValue();
            if (emiRatio > 0.5) emiScore = 15;
            else if (emiRatio > 0.4) emiScore = 35;
            else if (emiRatio > 0.3) emiScore = 55;
            else if (emiRatio > 0.2) emiScore = 75;
            else emiScore = 95;
        }

        int debtAssetScore = 100;
        if (totalAssets.compareTo(BigDecimal.ZERO) > 0) {
            double ratio = totalDebt.divide(totalAssets, 4, RoundingMode.HALF_UP).doubleValue();
            if (ratio > 0.8) debtAssetScore = 10;
            else if (ratio > 0.6) debtAssetScore = 30;
            else if (ratio > 0.4) debtAssetScore = 55;
            else if (ratio > 0.2) debtAssetScore = 75;
            else debtAssetScore = 95;
        }

        // 60% weight on EMI-to-income, 40% on debt-to-assets
        return (emiScore * 60 + debtAssetScore * 40) / 100;
    }

    // --- Dimension 3: Savings Rate (15%) ---
    // Approximated from total assets relative to income history
    private int scoreSavingsRate(BigDecimal totalAssets, BigDecimal monthlyIncome, List<Holding> holdings) {
        if (monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            // No income data — score based on whether they have any assets
            return holdings.isEmpty() ? 30 : 60;
        }

        // Assets-to-annual-income ratio as a proxy for savings discipline
        BigDecimal annualIncome = monthlyIncome.multiply(BigDecimal.valueOf(12));
        if (annualIncome.compareTo(BigDecimal.ZERO) <= 0) return 50;

        double assetsToIncome = totalAssets.divide(annualIncome, 4, RoundingMode.HALF_UP).doubleValue();
        if (assetsToIncome >= 5) return 100;
        if (assetsToIncome >= 3) return 85;
        if (assetsToIncome >= 1.5) return 70;
        if (assetsToIncome >= 0.5) return 50;
        if (assetsToIncome >= 0.1) return 30;
        return 10;
    }

    // --- Dimension 4: Diversification (15%) ---
    // Asset class count + concentration check (no single class > 60%)
    private int scoreDiversification(Map<AssetType, BigDecimal> breakdown, BigDecimal totalAssets) {
        if (breakdown.isEmpty()) return 0;
        int classCount = breakdown.size();

        // Check concentration — max allocation in any single class
        double maxConcentration = 0;
        if (totalAssets.compareTo(BigDecimal.ZERO) > 0) {
            for (BigDecimal val : breakdown.values()) {
                double pct = val.divide(totalAssets, 4, RoundingMode.HALF_UP).doubleValue();
                if (pct > maxConcentration) maxConcentration = pct;
            }
        }

        int classScore;
        if (classCount >= 5) classScore = 100;
        else if (classCount >= 4) classScore = 80;
        else if (classCount >= 3) classScore = 65;
        else if (classCount >= 2) classScore = 45;
        else classScore = 20;

        int concentrationScore;
        if (maxConcentration <= 0.40) concentrationScore = 100;
        else if (maxConcentration <= 0.50) concentrationScore = 80;
        else if (maxConcentration <= 0.60) concentrationScore = 60;
        else if (maxConcentration <= 0.75) concentrationScore = 35;
        else concentrationScore = 15;

        // 50% class count, 50% concentration
        return (classScore + concentrationScore) / 2;
    }

    // --- Dimension 5: Liquidity (15%) ---
    // Truly liquid assets (can sell within a week) vs total
    private int scoreLiquidity(BigDecimal liquidAssets, BigDecimal totalAssets) {
        if (totalAssets.compareTo(BigDecimal.ZERO) <= 0) return 50;
        double ratio = liquidAssets.divide(totalAssets, 4, RoundingMode.HALF_UP).doubleValue();
        if (ratio >= 0.40) return 100;
        if (ratio >= 0.30) return 80;
        if (ratio >= 0.20) return 60;
        if (ratio >= 0.10) return 40;
        return 15;
    }

    // --- Dimension 6: Goal Progress (15%) ---
    // Are goals on track? Considers completion % and time remaining
    private int scoreGoalProgress(List<Goal> goals) {
        if (goals.isEmpty()) return 50; // No goals set — neutral

        List<Goal> activeGoals = goals.stream()
                .filter(g -> g.getDeletedAt() == null)
                .filter(g -> g.getTargetAmount() != null && g.getTargetAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (activeGoals.isEmpty()) return 50;

        double totalScore = 0;
        for (Goal g : activeGoals) {
            BigDecimal current = g.getCurrentAmount() != null ? g.getCurrentAmount() : BigDecimal.ZERO;
            double completionPct = current.divide(g.getTargetAmount(), 4, RoundingMode.HALF_UP).doubleValue();

            if (g.getTargetDate() != null) {
                long totalDays = ChronoUnit.DAYS.between(
                        g.getCreatedAt() != null ? g.getCreatedAt().toLocalDate() : LocalDate.now().minusYears(1),
                        g.getTargetDate());
                long daysElapsed = ChronoUnit.DAYS.between(
                        g.getCreatedAt() != null ? g.getCreatedAt().toLocalDate() : LocalDate.now().minusYears(1),
                        LocalDate.now());

                if (totalDays > 0) {
                    double expectedPct = Math.min((double) daysElapsed / totalDays, 1.0);
                    // On track if completion >= expected
                    double trackRatio = expectedPct > 0 ? completionPct / expectedPct : completionPct;
                    if (trackRatio >= 1.0) totalScore += 100;
                    else if (trackRatio >= 0.75) totalScore += 75;
                    else if (trackRatio >= 0.5) totalScore += 50;
                    else totalScore += 25;
                } else {
                    totalScore += completionPct >= 1.0 ? 100 : completionPct * 100;
                }
            } else {
                // No deadline — score purely on completion
                totalScore += Math.min(completionPct * 100, 100);
            }
        }

        return (int) (totalScore / activeGoals.size());
    }

    // --- Recommendations ---
    private List<String> buildRecommendations(
            int emergencyScore, int debtScore, int savingsScore, int diversificationScore,
            int liquidityScore, int goalScore,
            BigDecimal liquidAssets, BigDecimal monthlyEMI, BigDecimal monthlyIncome,
            BigDecimal totalDebt, BigDecimal totalAssets, List<Goal> goals,
            Map<AssetType, BigDecimal> assetBreakdown) {

        List<String> recs = new ArrayList<>();

        if (emergencyScore < 65) {
            recs.add("Build your emergency fund to cover at least 6 months of expenses in liquid assets (savings account, liquid MFs).");
        }
        if (debtScore < 60 && monthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
            double emiRatio = monthlyEMI.divide(monthlyIncome, 2, RoundingMode.HALF_UP).doubleValue();
            recs.add(String.format("Your EMI-to-income ratio is %.0f%%. Try to keep it below 30%% by prepaying high-interest loans.", emiRatio * 100));
        }
        if (savingsScore < 50) {
            recs.add("Increase your savings rate. Aim to invest at least 20%% of your income through SIPs or recurring deposits.");
        }
        if (diversificationScore < 60) {
            if (assetBreakdown.size() < 3) {
                recs.add("Diversify across more asset classes — consider adding debt funds, gold, or PPF alongside equities.");
            } else {
                recs.add("Your portfolio is concentrated in one asset class. Rebalance to spread risk across equity, debt, and gold.");
            }
        }
        if (liquidityScore < 50) {
            recs.add("Increase liquid holdings. Move some funds to savings accounts or liquid mutual funds for easy access.");
        }
        if (goalScore < 50 && !goals.isEmpty()) {
            recs.add("You're behind on some financial goals. Review your SIP amounts or extend your timelines.");
        }
        if (goals.isEmpty()) {
            recs.add("Set financial goals (retirement, house, education) to give your investments a clear purpose.");
        }
        if (recs.isEmpty()) {
            recs.add("Great financial health! Keep maintaining your diversified portfolio and savings discipline.");
        }

        return recs;
    }

    // --- Helpers ---

    /** Truly liquid = can convert to cash within a week */
    private boolean isTrulyLiquid(AssetType type) {
        return switch (type) {
            case EQUITY, ETF, MUTUAL_FUND, CASH, FD, BOND, CRYPTO -> true;
            // EPF, PPF, NPS are locked-in — NOT liquid
            case EPF, PPF, NPS, REAL_ESTATE, GOLD, SGB -> false;
        };
    }

    private BigDecimal getLatestMonthlyIncome(UUID userId) {
        return salaryRepository.findByUserIdOrderByPayDateDesc(userId).stream()
                .filter(s -> s.getAmount() != null)
                .map(Salary::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private int countAssetClasses(List<Holding> holdings) {
        return (int) holdings.stream().map(Holding::getAssetType).distinct().count();
    }

    private String getStatusFromScore(int score) {
        if (score >= 70) return "Healthy";
        if (score >= 40) return "Moderate";
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
