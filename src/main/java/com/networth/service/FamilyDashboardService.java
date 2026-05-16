package com.networth.service;

import com.networth.model.dto.NetWorthResponse;
import com.networth.service.networth.NetWorthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FamilyDashboardService {

    private final NetWorthService netWorthService;
    private final HealthScoreService healthScoreService;

    @Transactional(readOnly = true)
    public Map<String, Object> getFamilyNetWorth(List<UUID> memberUserIds) {
        BigDecimal familyTotalAssets = BigDecimal.ZERO;
        BigDecimal familyTotalLiabilities = BigDecimal.ZERO;
        Map<String, Object> memberBreakdowns = new LinkedHashMap<>();

        for (UUID userId : memberUserIds) {
            try {
                NetWorthResponse memberNetWorth = netWorthService.calculateNetWorth(userId);
                familyTotalAssets = familyTotalAssets.add(memberNetWorth.getTotalAssets());
                familyTotalLiabilities = familyTotalLiabilities.add(memberNetWorth.getTotalLiabilities());

                memberBreakdowns.put(userId.toString(), Map.of(
                        "totalAssets", memberNetWorth.getTotalAssets(),
                        "totalLiabilities", memberNetWorth.getTotalLiabilities(),
                        "netWorth", memberNetWorth.getNetWorth(),
                        "equityValue", memberNetWorth.getEquityValue(),
                        "debtValue", memberNetWorth.getDebtValue()
                ));
            } catch (Exception e) {
                memberBreakdowns.put(userId.toString(), Map.of("error", "Could not fetch data"));
            }
        }

        return Map.of(
                "familyTotalAssets", familyTotalAssets,
                "familyTotalLiabilities", familyTotalLiabilities,
                "familyNetWorth", familyTotalAssets.subtract(familyTotalLiabilities),
                "memberCount", memberUserIds.size(),
                "memberBreakdowns", memberBreakdowns
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFamilyHealthScore(List<UUID> memberUserIds) {
        List<Map<String, Object>> scores = new ArrayList<>();
        BigDecimal avgScore = BigDecimal.ZERO;

        for (UUID userId : memberUserIds) {
            try {
                Map<String, Object> healthScore = healthScoreService.calculateHealthScore(userId);
                int score = (int) healthScore.get("totalScore");
                scores.add(Map.of(
                        "userId", userId,
                        "score", score,
                        "grade", healthScore.get("grade")
                ));
                avgScore = avgScore.add(BigDecimal.valueOf(score));
            } catch (Exception e) {
                scores.add(Map.of("userId", userId, "score", 0, "grade", "N/A"));
            }
        }

        BigDecimal avg = memberUserIds.isEmpty() ? BigDecimal.ZERO
                : avgScore.divide(BigDecimal.valueOf(memberUserIds.size()), RoundingMode.HALF_UP);

        return Map.of(
                "averageScore", avg.setScale(0, RoundingMode.HALF_UP),
                "averageGrade", getGrade(avg.intValue()),
                "memberScores", scores
        );
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
