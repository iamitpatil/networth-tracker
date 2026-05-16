package com.networth.service;

import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AIInsightsService {

    private final HoldingRepository holdingRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> generateInsights(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        List<Map<String, Object>> insights = new ArrayList<>();

        if (holdings.isEmpty()) {
            insights.add(buildInsight("empty_portfolio", "info", "Add your first investment to start tracking your wealth."));
            return insights;
        }

        insights.addAll(checkConcentrationRisk(holdings));
        insights.addAll(checkEmergencyFund(holdings));
        insights.addAll(checkDebtEquityBalance(holdings));
        insights.addAll(checkSectorConcentration(holdings));
        insights.addAll(checkDiversification(holdings));
        insights.addAll(checkUnderperformingAssets(holdings));
        insights.addAll(checkTaxHarvesting(holdings));

        insights.sort(Comparator.comparing(
                (Map<String, Object> i) -> (String) i.get("severity")
        ).reversed());

        return insights;
    }

    private List<Map<String, Object>> checkConcentrationRisk(List<Holding> holdings) {
        List<Map<String, Object>> insights = new ArrayList<>();

        BigDecimal totalValue = holdings.stream()
                .map(h -> h.getCurrentValue() != null ? h.getCurrentValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalValue.compareTo(BigDecimal.ZERO) == 0) return insights;

        Map<AssetType, BigDecimal> assetValues = holdings.stream()
                .collect(Collectors.groupingBy(
                        Holding::getAssetType,
                        Collectors.mapping(
                                h -> h.getCurrentValue() != null ? h.getCurrentValue() : BigDecimal.ZERO,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                        )
                ));

        for (Map.Entry<AssetType, BigDecimal> entry : assetValues.entrySet()) {
            BigDecimal percentage = entry.getValue()
                    .divide(totalValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            if (percentage.compareTo(BigDecimal.valueOf(70)) > 0) {
                insights.add(buildInsight(
                        "high_concentration",
                        "critical",
                        String.format("Your portfolio is %.1f%% in %s. Consider diversifying to reduce risk.",
                                percentage.doubleValue(), entry.getKey().toString().toLowerCase())
                ));
            }
        }

        return insights;
    }

    private List<Map<String, Object>> checkEmergencyFund(List<Holding> holdings) {
        List<Map<String, Object>> insights = new ArrayList<>();

        BigDecimal liquidValue = holdings.stream()
                .filter(h -> isLiquid(h.getAssetType()))
                .map(h -> h.getCurrentValue() != null ? h.getCurrentValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal estimatedMonthlyExpense = liquidValue.divide(BigDecimal.valueOf(6), RoundingMode.HALF_UP);

        if (liquidValue.compareTo(estimatedMonthlyExpense.multiply(BigDecimal.valueOf(3))) < 0) {
            insights.add(buildInsight(
                    "low_emergency_fund",
                    "warning",
                    "Your liquid assets cover less than 3 months of expenses. Build an emergency fund first."
            ));
        }

        return insights;
    }

    private List<Map<String, Object>> checkDebtEquityBalance(List<Holding> holdings) {
        List<Map<String, Object>> insights = new ArrayList<>();

        BigDecimal equityValue = BigDecimal.ZERO;
        BigDecimal debtValue = BigDecimal.ZERO;

        for (Holding h : holdings) {
            BigDecimal val = h.getCurrentValue() != null ? h.getCurrentValue() : BigDecimal.ZERO;
            if (h.getAssetType() == AssetType.EQUITY || h.getAssetType() == AssetType.ETF) {
                equityValue = equityValue.add(val);
            } else if (h.getAssetType() == AssetType.MUTUAL_FUND || h.getAssetType() == AssetType.FD
                    || h.getAssetType() == AssetType.BOND || h.getAssetType() == AssetType.EPF
                    || h.getAssetType() == AssetType.PPF || h.getAssetType() == AssetType.NPS) {
                debtValue = debtValue.add(val);
            }
        }

        BigDecimal total = equityValue.add(debtValue);
        if (total.compareTo(BigDecimal.ZERO) == 0) return insights;

        BigDecimal equityPct = equityValue.divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        if (equityPct.compareTo(BigDecimal.valueOf(90)) > 0) {
            insights.add(buildInsight(
                    "too_much_equity",
                    "warning",
                    String.format("Equity allocation is %.1f%%. Consider adding debt instruments for stability.", equityPct.doubleValue())
            ));
        } else if (equityPct.compareTo(BigDecimal.valueOf(20)) < 0) {
            insights.add(buildInsight(
                    "too_much_debt",
                    "info",
                    String.format("Debt allocation is %.1f%%. Consider increasing equity for long-term growth.",
                            debtValue.divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue())
            ));
        }

        return insights;
    }

    private List<Map<String, Object>> checkSectorConcentration(List<Holding> holdings) {
        List<Map<String, Object>> insights = new ArrayList<>();

        Map<String, BigDecimal> sectorValues = holdings.stream()
                .filter(h -> h.getSector() != null)
                .collect(Collectors.groupingBy(
                        Holding::getSector,
                        Collectors.mapping(
                                h -> h.getCurrentValue() != null ? h.getCurrentValue() : BigDecimal.ZERO,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                        )
                ));

        BigDecimal totalValue = sectorValues.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalValue.compareTo(BigDecimal.ZERO) == 0) return insights;

        for (Map.Entry<String, BigDecimal> entry : sectorValues.entrySet()) {
            BigDecimal pct = entry.getValue().divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            if (pct.compareTo(BigDecimal.valueOf(40)) > 0) {
                insights.add(buildInsight(
                        "sector_concentration",
                        "warning",
                        String.format("%.1f%% exposure to %s sector. High sector concentration increases risk.",
                                pct.doubleValue(), entry.getKey())
                ));
            }
        }

        return insights;
    }

    private List<Map<String, Object>> checkDiversification(List<Holding> holdings) {
        List<Map<String, Object>> insights = new ArrayList<>();

        long uniqueAssetTypes = holdings.stream().map(Holding::getAssetType).distinct().count();

        if (uniqueAssetTypes < 3) {
            insights.add(buildInsight(
                    "low_diversification",
                    "warning",
                    String.format("Only %d asset class(es). Consider adding mutual funds, gold, or debt instruments.", uniqueAssetTypes)
            ));
        }

        if (holdings.size() < 5) {
            insights.add(buildInsight(
                    "few_holdings",
                    "info",
                    "Consider diversifying across more stocks and asset classes for better risk management."
            ));
        }

        return insights;
    }

    private List<Map<String, Object>> checkUnderperformingAssets(List<Holding> holdings) {
        List<Map<String, Object>> insights = new ArrayList<>();

        for (Holding holding : holdings) {
            if (holding.getCurrentValue() != null && holding.getUnrealizedPnl() != null) {
                BigDecimal costBasis = holding.getQuantity().multiply(holding.getAverageBuyPrice());
                if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal returnPct = holding.getUnrealizedPnl()
                            .divide(costBasis, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

                    if (returnPct.compareTo(BigDecimal.valueOf(-20)) < 0) {
                        insights.add(buildInsight(
                                "underperforming_asset",
                                "warning",
                                String.format("%s is down %.1f%%. Review if fundamentals have changed.",
                                        holding.getSymbol(), returnPct.doubleValue())
                        ));
                    }
                }
            }
        }

        return insights;
    }

    private List<Map<String, Object>> checkTaxHarvesting(List<Holding> holdings) {
        List<Map<String, Object>> insights = new ArrayList<>();

        BigDecimal totalUnrealizedGain = BigDecimal.ZERO;
        for (Holding h : holdings) {
            if (h.getUnrealizedPnl() != null && h.getUnrealizedPnl().compareTo(BigDecimal.ZERO) > 0) {
                totalUnrealizedGain = totalUnrealizedGain.add(h.getUnrealizedPnl());
            }
        }

        if (totalUnrealizedGain.compareTo(new BigDecimal("100000")) > 0) {
            insights.add(buildInsight(
                    "tax_harvesting_opportunity",
                    "info",
                    "You have significant unrealized gains. Consider tax-loss harvesting to offset gains before year-end."
            ));
        }

        return insights;
    }

    private Map<String, Object> buildInsight(String type, String severity, String message) {
        return Map.of(
                "type", type,
                "severity", severity,
                "message", message,
                "timestamp", LocalDate.now().toString()
        );
    }

    private boolean isLiquid(AssetType type) {
        return switch (type) {
            case EQUITY, ETF, MUTUAL_FUND, CASH, FD, BOND, CRYPTO -> true;
            default -> false;
        };
    }
}
