package com.networth.service;

import com.networth.model.dto.TransactionResponse;
import com.networth.model.entity.Holding;
import com.networth.model.entity.StockPriceHistory;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.StockPriceHistoryRepository;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes investment portfolio value over time using:
 * 1. Transaction history to track positions (qty held per holding per day)
 * 2. Daily historical prices from stock_price_history to value each position
 * 3. Falls back to transaction price when no market data is available
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentOverTimeService {

    private final TransactionService transactionService;
    private final HoldingRepository holdingRepository;
    private final HoldingService holdingService;
    private final StockPriceHistoryRepository priceHistoryRepository;

    private static final Set<String> INVEST_TXNS = Set.of("BUY", "SIP", "LUMPSUM", "DEPOSIT", "CONTRIBUTION", "OPEN");
    private static final Set<String> DIVEST_TXNS = Set.of("SELL", "WITHDRAWAL", "WITHDRAW");

    public List<Map<String, Object>> getInvestmentOverTime(UUID userId, int days) {
        return getInvestmentOverTime(userId, days, null);
    }

    public List<Map<String, Object>> getInvestmentOverTime(UUID userId, int days, AssetType assetType) {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.minusDays(days);

        // Get holdings and their pricing symbols
        List<Holding> holdings = assetType != null
                ? holdingRepository.findByUserIdAndAssetType(userId, assetType)
                : holdingRepository.findByUserId(userId);

        Set<UUID> holdingIds = holdings.stream().map(Holding::getId).collect(Collectors.toSet());

        // Map holdingId → pricing symbol (ISIN for MFs, symbol for equities)
        Map<UUID, String> holdingPricingSymbol = new HashMap<>();
        for (Holding h : holdings) {
            holdingPricingSymbol.put(h.getId(), holdingService.getEffectiveSymbolForPricing(h));
        }

        // Load all transactions sorted by date
        List<TransactionResponse> allTxns = transactionService.getUserTransactions(userId.toString()).stream()
                .filter(t -> holdingIds.contains(UUID.fromString(t.getHoldingId())))
                .sorted(Comparator.comparing(t -> t.getTransactionDate().toLocalDate()))
                .toList();

        // Load historical prices for all relevant symbols in the date range
        Set<String> pricingSymbols = new HashSet<>(holdingPricingSymbol.values());
        Map<String, Map<LocalDate, BigDecimal>> priceMap = loadPriceHistory(pricingSymbols, cutoff, today);

        // Build position timeline: track qty per holding at each transaction date
        Map<UUID, BigDecimal> holdingQty = new HashMap<>();
        Map<UUID, BigDecimal> holdingLastTxnPrice = new HashMap<>();
        BigDecimal runningInvested = BigDecimal.ZERO;

        // Process all transactions (including before cutoff for position tracking)
        List<TxnEvent> txnEvents = new ArrayList<>();
        for (TransactionResponse t : allTxns) {
            LocalDate date = t.getTransactionDate().toLocalDate();
            UUID holdingId = UUID.fromString(t.getHoldingId());
            BigDecimal txQty = t.getQuantity() != null ? t.getQuantity().abs() : BigDecimal.ZERO;
            BigDecimal txPrice = t.getPrice() != null ? t.getPrice() : BigDecimal.ZERO;
            BigDecimal txAmount = t.getAmount() != null ? t.getAmount() : txPrice.multiply(txQty);

            if (INVEST_TXNS.contains(t.getTransactionType().name())) {
                holdingQty.merge(holdingId, txQty, BigDecimal::add);
                runningInvested = runningInvested.add(txAmount);
            } else if (DIVEST_TXNS.contains(t.getTransactionType().name())) {
                holdingQty.merge(holdingId, txQty.negate(), BigDecimal::add);
                runningInvested = runningInvested.subtract(txAmount);
            }
            if (txPrice.compareTo(BigDecimal.ZERO) > 0) {
                holdingLastTxnPrice.put(holdingId, txPrice);
            }

            txnEvents.add(new TxnEvent(date, new HashMap<>(holdingQty), new HashMap<>(holdingLastTxnPrice), runningInvested));
        }

        // Generate data points: one per week (or per day for short ranges)
        int interval = days <= 90 ? 1 : days <= 365 ? 7 : 30;
        List<Map<String, Object>> result = new ArrayList<>();

        // Determine the snapshot of positions at the cutoff
        Map<UUID, BigDecimal> positionsAtCutoff = new HashMap<>();
        Map<UUID, BigDecimal> pricesAtCutoff = new HashMap<>();
        BigDecimal investedAtCutoff = BigDecimal.ZERO;
        for (TxnEvent evt : txnEvents) {
            if (evt.date.isAfter(cutoff)) break;
            positionsAtCutoff = evt.positions;
            pricesAtCutoff = evt.lastPrices;
            investedAtCutoff = evt.invested;
        }

        Map<UUID, BigDecimal> currentPositions = new HashMap<>(positionsAtCutoff);
        Map<UUID, BigDecimal> currentTxnPrices = new HashMap<>(pricesAtCutoff);
        BigDecimal currentInvested = investedAtCutoff;

        // Track which txn event index we're at
        int txnIdx = 0;
        // Skip events before cutoff
        while (txnIdx < txnEvents.size() && !txnEvents.get(txnIdx).date.isAfter(cutoff)) {
            txnIdx++;
        }

        LocalDate date = cutoff;
        while (!date.isAfter(today)) {
            // Apply any transactions on or before this date
            while (txnIdx < txnEvents.size() && !txnEvents.get(txnIdx).date.isAfter(date)) {
                TxnEvent evt = txnEvents.get(txnIdx);
                currentPositions = evt.positions;
                currentTxnPrices = evt.lastPrices;
                currentInvested = evt.invested;
                txnIdx++;
            }

            // Compute portfolio value using historical prices for this date
            BigDecimal value = computeValueAtDate(date, currentPositions, currentTxnPrices,
                    holdingPricingSymbol, priceMap);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date.toString());
            point.put("invested", currentInvested.setScale(2, RoundingMode.HALF_UP));
            point.put("value", value.setScale(2, RoundingMode.HALF_UP));
            result.add(point);

            date = date.plusDays(interval);
        }

        // Always add today as the last point
        if (result.isEmpty() || !result.get(result.size() - 1).get("date").equals(today.toString())) {
            BigDecimal todayValue = computeValueAtDate(today, currentPositions, currentTxnPrices,
                    holdingPricingSymbol, priceMap);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", today.toString());
            point.put("invested", currentInvested.setScale(2, RoundingMode.HALF_UP));
            point.put("value", todayValue.setScale(2, RoundingMode.HALF_UP));
            result.add(point);
        }

        return result;
    }

    /**
     * Compute total portfolio value at a specific date using historical prices.
     * For each holding: qty × price_on_date.
     * Falls back to last transaction price if no market data.
     */
    private BigDecimal computeValueAtDate(LocalDate date, Map<UUID, BigDecimal> positions,
                                           Map<UUID, BigDecimal> txnPrices,
                                           Map<UUID, String> holdingPricingSymbol,
                                           Map<String, Map<LocalDate, BigDecimal>> priceMap) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<UUID, BigDecimal> entry : positions.entrySet()) {
            BigDecimal qty = entry.getValue();
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            UUID holdingId = entry.getKey();
            String pricingSymbol = holdingPricingSymbol.get(holdingId);
            BigDecimal price = null;

            // Try historical price for this date (or closest before)
            if (pricingSymbol != null && priceMap.containsKey(pricingSymbol)) {
                Map<LocalDate, BigDecimal> symbolPrices = priceMap.get(pricingSymbol);
                price = symbolPrices.get(date);
                // If no price on exact date, find the closest previous date
                if (price == null) {
                    for (int i = 1; i <= 7; i++) {
                        price = symbolPrices.get(date.minusDays(i));
                        if (price != null) break;
                    }
                }
            }

            // Fallback to last transaction price
            if (price == null) {
                price = txnPrices.getOrDefault(holdingId, BigDecimal.ZERO);
            }

            if (price.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(qty.multiply(price));
            }
        }
        return total;
    }

    /**
     * Bulk-load historical prices for all symbols in the date range.
     * Returns: symbol → (date → close price)
     */
    private Map<String, Map<LocalDate, BigDecimal>> loadPriceHistory(Set<String> symbols, LocalDate from, LocalDate to) {
        Map<String, Map<LocalDate, BigDecimal>> result = new HashMap<>();
        for (String symbol : symbols) {
            if (symbol == null) continue;
            List<StockPriceHistory> history = priceHistoryRepository
                    .findBySymbolAndPriceDateBetweenOrderByPriceDate(symbol, from, to);
            if (!history.isEmpty()) {
                Map<LocalDate, BigDecimal> dateMap = new LinkedHashMap<>();
                for (StockPriceHistory h : history) {
                    if (h.getClose() != null) {
                        dateMap.put(h.getPriceDate(), h.getClose());
                    }
                }
                result.put(symbol, dateMap);
            }
        }
        return result;
    }

    private record TxnEvent(LocalDate date, Map<UUID, BigDecimal> positions,
                             Map<UUID, BigDecimal> lastPrices, BigDecimal invested) {}
}
