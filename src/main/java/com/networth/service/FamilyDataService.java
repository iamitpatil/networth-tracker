package com.networth.service;

import com.networth.model.entity.Goal;
import com.networth.model.entity.Liability;
import com.networth.model.entity.User;
import com.networth.model.dto.*;
import com.networth.repository.UserRepository;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.PortfolioSummaryService;
import com.networth.service.portfolio.TransactionService;
import com.networth.service.networth.NetWorthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final UserRepository userRepository;

    /**
     * Build a map of userId -> user name for all approved family members.
     * Used to enrich responses with owner information.
     */
    private Map<UUID, String> buildMemberNameMap(List<UUID> memberIds) {
        Map<UUID, String> map = new HashMap<>();
        for (UUID id : memberIds) {
            userRepository.findById(id).ifPresent(user ->
                    map.put(id, user.getName() != null ? user.getName() : user.getEmail())
            );
        }
        return map;
    }

    public List<HoldingResponse> getHoldings(UUID userId, boolean familyView) {
        if (!familyView) {
            return holdingService.getUserHoldings(userId.toString());
        }
        List<UUID> memberIds = familyService.getApprovedMemberIds(userId);
        Map<UUID, String> nameMap = buildMemberNameMap(memberIds);

        List<HoldingResponse> all = new ArrayList<>();
        for (UUID mid : memberIds) {
            List<HoldingResponse> memberHoldings = holdingService.getUserHoldings(mid.toString());
            String memberName = nameMap.getOrDefault(mid, "Family member");
            String memberIdStr = mid.toString();
            // Enrich with owner info
            for (HoldingResponse h : memberHoldings) {
                h.setOwnerId(memberIdStr);
                h.setOwnerName(memberName);
            }
            all.addAll(memberHoldings);
        }
        return all;
    }

    public List<TransactionResponse> getTransactions(UUID userId, boolean familyView) {
        if (!familyView) {
            return transactionService.getUserTransactions(userId.toString());
        }
        List<UUID> memberIds = familyService.getApprovedMemberIds(userId);
        Map<UUID, String> nameMap = buildMemberNameMap(memberIds);

        List<TransactionResponse> all = new ArrayList<>();
        for (UUID mid : memberIds) {
            List<TransactionResponse> memberTxns = transactionService.getUserTransactions(mid.toString());
            String memberName = nameMap.getOrDefault(mid, "Family member");
            for (TransactionResponse t : memberTxns) {
                t.setOwnerId(mid.toString());
                t.setOwnerName(memberName);
            }
            all.addAll(memberTxns);
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
