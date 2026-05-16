package com.networth.service;

import com.networth.model.dto.TransactionResponse;
import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.service.portfolio.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvestmentOverTimeService {

    private final TransactionService transactionService;
    private final NetWorthHistoryService netWorthHistoryService;
    private final HoldingRepository holdingRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String HISTORY_KEY_PREFIX = "networth:history:";
    private static final Set<String> INVEST_TXNS = Set.of("BUY", "SIP", "LUMPSUM", "DEPOSIT", "CONTRIBUTION", "OPEN");
    private static final Set<String> DIVEST_TXNS = Set.of("SELL", "WITHDRAWAL", "WITHDRAW");

    public List<Map<String, Object>> getInvestmentOverTime(UUID userId, int days) {
        return getInvestmentOverTime(userId, days, null);
    }

    public List<Map<String, Object>> getInvestmentOverTime(UUID userId, int days, AssetType assetType) {
        LocalDate cutoff = LocalDate.now().minusDays(days);

        List<TransactionResponse> allTransactions = transactionService.getUserTransactions(userId.toString());
        Set<UUID> filteredHoldingIds = getFilteredHoldingIds(userId, assetType);
        List<TransactionResponse> transactions = allTransactions.stream()
                .filter(t -> filteredHoldingIds.contains(UUID.fromString(t.getHoldingId())))
                .toList();
        Map<LocalDate, BigDecimal> dailyInvested = new TreeMap<>();
        for (TransactionResponse t : transactions) {
            LocalDate date = t.getTransactionDate().toLocalDate();
            if (date.isBefore(cutoff)) continue;
            BigDecimal amount = t.getAmount() != null ? t.getAmount() : t.getPrice().multiply(t.getQuantity());
            if (INVEST_TXNS.contains(t.getTransactionType().name())) {
                dailyInvested.merge(date, amount, BigDecimal::add);
            } else if (DIVEST_TXNS.contains(t.getTransactionType().name())) {
                dailyInvested.merge(date, amount.negate(), BigDecimal::add);
            }
        }

        BigDecimal runningInvested = BigDecimal.ZERO;
        Map<LocalDate, BigDecimal> cumulativeInvested = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : dailyInvested.entrySet()) {
            runningInvested = runningInvested.add(entry.getValue());
            cumulativeInvested.put(entry.getKey(), runningInvested);
        }

        List<Map<String, Object>> history = netWorthHistoryService.getNetWorthHistory(userId, days);

        if (history.isEmpty() && !filteredHoldingIds.isEmpty()) {
            netWorthHistoryService.snapshotNetWorth(userId);
            history = netWorthHistoryService.getNetWorthHistory(userId, days);
        }

        Set<LocalDate> dates = new TreeSet<>();
        dates.addAll(cumulativeInvested.keySet());
        for (Map<String, Object> h : history) {
            dates.add(LocalDate.parse(h.get("date").toString()));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate date : dates) {
            BigDecimal invested = cumulativeInvested.get(date);
            if (invested == null) {
                Optional<LocalDate> prev = cumulativeInvested.keySet().stream()
                        .filter(d -> !d.isAfter(date)).max(Comparator.naturalOrder());
                invested = prev.map(cumulativeInvested::get).orElse(BigDecimal.ZERO);
            }

            BigDecimal value = null;
            for (Map<String, Object> h : history) {
                if (date.equals(LocalDate.parse(h.get("date").toString()))) {
                    value = getValueForAssetType(h, assetType);
                    break;
                }
            }
            if (value == null && !history.isEmpty()) {
                Map<String, Object> latest = history.getLast();
                value = getValueForAssetType(latest, assetType);
            }

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date.toString());
            point.put("invested", invested.setScale(2, RoundingMode.HALF_UP));
            point.put("value", value != null ? value.setScale(2, RoundingMode.HALF_UP) : null);
            result.add(point);
        }

        return result;
    }

    private Set<UUID> getFilteredHoldingIds(UUID userId, AssetType assetType) {
        if (assetType == null) {
            return holdingRepository.findByUserId(userId).stream()
                    .map(Holding::getId)
                    .collect(Collectors.toSet());
        }
        return holdingRepository.findByUserIdAndAssetType(userId, assetType).stream()
                .map(Holding::getId)
                .collect(Collectors.toSet());
    }

    private BigDecimal getValueForAssetType(Map<String, Object> snapshot, AssetType assetType) {
        if (assetType == null) {
            BigDecimal equity = (BigDecimal) snapshot.getOrDefault("equityValue", BigDecimal.ZERO);
            BigDecimal debt = (BigDecimal) snapshot.getOrDefault("debtValue", BigDecimal.ZERO);
            BigDecimal gold = snapshot.containsKey("goldValue") && snapshot.get("goldValue") instanceof BigDecimal g ? g : BigDecimal.ZERO;
            return equity.add(debt).add(gold);
        }
        return switch (assetType) {
            case EQUITY, ETF -> (BigDecimal) snapshot.getOrDefault("equityValue", BigDecimal.ZERO);
            case GOLD, SGB -> (BigDecimal) snapshot.getOrDefault("goldValue", BigDecimal.ZERO);
            default -> (BigDecimal) snapshot.getOrDefault("debtValue", BigDecimal.ZERO);
        };
    }
}
