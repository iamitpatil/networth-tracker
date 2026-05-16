package com.networth.service;

import com.networth.model.entity.Goal;
import com.networth.model.entity.Liability;
import com.networth.model.entity.Holding;
import com.networth.model.dto.*;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.PortfolioSummaryService;
import com.networth.service.portfolio.TransactionService;
import com.networth.service.networth.NetWorthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FamilyDataService {

    private final FamilyService familyService;
    private final HoldingService holdingService;
    private final TransactionService transactionService;
    private final PortfolioSummaryService portfolioSummaryService;
    private final GoalService goalService;
    private final EMIService emiService;
    private final NetWorthService netWorthService;
    private final FamilyDashboardService familyDashboardService;

    public List<HoldingResponse> getHoldings(UUID userId, boolean familyView) {
        if (!familyView) {
            return holdingService.getUserHoldings(userId.toString());
        }
        List<UUID> memberIds = familyService.getApprovedMemberIds(userId);
        List<HoldingResponse> all = new ArrayList<>();
        for (UUID mid : memberIds) {
            all.addAll(holdingService.getUserHoldings(mid.toString()));
        }
        return all;
    }

    public List<TransactionResponse> getTransactions(UUID userId, boolean familyView) {
        if (!familyView) {
            return transactionService.getUserTransactions(userId.toString());
        }
        List<UUID> memberIds = familyService.getApprovedMemberIds(userId);
        List<TransactionResponse> all = new ArrayList<>();
        for (UUID mid : memberIds) {
            all.addAll(transactionService.getUserTransactions(mid.toString()));
        }
        return all;
    }

    public PortfolioSummary getSummary(UUID userId, boolean familyView) {
        if (!familyView) {
            return portfolioSummaryService.getSummary(userId);
        }
        List<UUID> memberIds = familyService.getApprovedMemberIds(userId);
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalUnrealizedPnl = BigDecimal.ZERO;
        BigDecimal totalRealizedPnl = BigDecimal.ZERO;
        int holdingCount = 0;

        for (UUID mid : memberIds) {
            PortfolioSummary s = portfolioSummaryService.getSummary(mid);
            totalInvested = totalInvested.add(s.getTotalInvested());
            totalValue = totalValue.add(s.getCurrentValue());
            totalUnrealizedPnl = totalUnrealizedPnl.add(s.getUnrealizedPnl());
            totalRealizedPnl = totalRealizedPnl.add(s.getRealizedPnl());
            holdingCount += s.getTotalHoldings();
        }

        return PortfolioSummary.builder()
                .totalInvested(totalInvested)
                .currentValue(totalValue)
                .unrealizedPnl(totalUnrealizedPnl)
                .realizedPnl(totalRealizedPnl)
                .totalHoldings(holdingCount)
                .build();
    }

    public List<Goal> getGoals(UUID userId, boolean familyView) {
        if (!familyView) return goalService.getUserGoals(userId);
        List<Goal> all = new ArrayList<>();
        for (UUID mid : familyService.getApprovedMemberIds(userId)) {
            all.addAll(goalService.getUserGoals(mid));
        }
        return all;
    }

    public List<Liability> getLiabilities(UUID userId, boolean familyView) {
        if (!familyView) return emiService.getUserLiabilities(userId);
        List<Liability> all = new ArrayList<>();
        for (UUID mid : familyService.getApprovedMemberIds(userId)) {
            all.addAll(emiService.getUserLiabilities(mid));
        }
        return all;
    }

    public Map<String, Object> getNetWorth(UUID userId, boolean familyView) {
        if (!familyView) {
            NetWorthResponse nw = netWorthService.calculateNetWorth(userId);
            return Map.of("totalAssets", nw.getTotalAssets(), "totalLiabilities", nw.getTotalLiabilities(), "netWorth", nw.getNetWorth());
        }
        List<UUID> members = familyService.getApprovedMemberIds(userId);
        return familyDashboardService.getFamilyNetWorth(members);
    }
}
