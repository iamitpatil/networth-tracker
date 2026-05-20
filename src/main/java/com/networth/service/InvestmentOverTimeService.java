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

        // Sort ALL transactions by date (including before cutoff — needed for position tracking)
        List<TransactionResponse> allFiltered = allTransactions.stream()
                .filter(t -> filteredHoldingIds.contains(UUID.fromString(t.getHoldingId())))
                .sorted(Comparator.comparing(t -> t.getTransactionDate().toLocalDate()))
                .toList();

        // Track per-holding: cumulative quantity and last known price
        Map<UUID, BigDecimal> holdingQty = new LinkedHashMap<>();
        Map<UUID, BigDecimal> holdingLastPrice = new LinkedHashMap<>();

        // Build timeline: for each transaction date, record cumulative invested + portfolio value
        // Value = sum of (qty held * last known price) for each holding AT that point in time
        BigDecimal runningInvested = BigDecimal.ZERO;
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate lastDate = null;

        for (TransactionResponse t : allFiltered) {
            LocalDate date = t.getTransactionDate().toLocalDate();
            UUID holdingId = UUID.fromString(t.getHoldingId());
            BigDecimal txQty = t.getQuantity() != null ? t.getQuantity().abs() : BigDecimal.ZERO;
            BigDecimal txPrice = t.getPrice() != null ? t.getPrice() : BigDecimal.ZERO;
            BigDecimal txAmount = t.getAmount() != null ? t.getAmount() : txPrice.multiply(txQty);

            // Update position and last known price for this holding
            if (INVEST_TXNS.contains(t.getTransactionType().name())) {
                holdingQty.merge(holdingId, txQty, BigDecimal::add);
                runningInvested = runningInvested.add(txAmount);
            } else if (DIVEST_TXNS.contains(t.getTransactionType().name())) {
                holdingQty.merge(holdingId, txQty.negate(), BigDecimal::add);
                runningInvested = runningInvested.subtract(txAmount);
            }
            // Always update last known price from transaction
            if (txPrice.compareTo(BigDecimal.ZERO) > 0) {
                holdingLastPrice.put(holdingId, txPrice);
            }

            // Only emit data points within the requested range
            if (date.isBefore(cutoff)) continue;
            // Avoid duplicate points for same date — skip if same as last emitted
            if (date.equals(lastDate)) {
                // Update the last point in result instead of adding a new one
                if (!result.isEmpty()) {
                    Map<String, Object> lastPoint = result.get(result.size() - 1);
                    lastPoint.put("invested", runningInvested.setScale(2, RoundingMode.HALF_UP));
                    lastPoint.put("value", computePortfolioValue(holdingQty, holdingLastPrice));
                }
                continue;
            }

            lastDate = date;
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date.toString());
            point.put("invested", runningInvested.setScale(2, RoundingMode.HALF_UP));
            point.put("value", computePortfolioValue(holdingQty, holdingLastPrice));
            result.add(point);
        }

        // Add today's point with current prices
        LocalDate today = LocalDate.now();
        if (lastDate == null || !lastDate.equals(today)) {
            List<Holding> currentHoldings = assetType != null
                    ? holdingRepository.findByUserIdAndAssetType(userId, assetType)
                    : holdingRepository.findByUserId(userId);
            BigDecimal currentValue = currentHoldings.stream()
                    .filter(h -> h.getCurrentValue() != null)
                    .map(Holding::getCurrentValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", today.toString());
            point.put("invested", runningInvested.setScale(2, RoundingMode.HALF_UP));
            point.put("value", currentValue.setScale(2, RoundingMode.HALF_UP));
            result.add(point);
        }

        return result;
    }

    private BigDecimal computePortfolioValue(Map<UUID, BigDecimal> holdingQty, Map<UUID, BigDecimal> holdingLastPrice) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<UUID, BigDecimal> entry : holdingQty.entrySet()) {
            BigDecimal qty = entry.getValue();
            BigDecimal price = holdingLastPrice.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (qty.compareTo(BigDecimal.ZERO) > 0 && price.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(qty.multiply(price));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
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
