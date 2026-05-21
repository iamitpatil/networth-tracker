package com.networth.service;

import com.networth.model.entity.Dividend;
import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.repository.DividendRepository;
import com.networth.repository.HoldingRepository;
import com.networth.service.market.provider.DividendEvent;
import com.networth.service.market.provider.MarketDataResolver;
import com.networth.service.portfolio.TransactionService;
import com.networth.model.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculates dividends earned per holding by:
 * 1. Fetching dividend events from NSE/Yahoo for each equity/ETF holding
 * 2. Computing quantity held on each record date from transaction history
 * 3. Calculating payout = qty held on record date * dividend per share
 * 4. Persisting as Dividend records (upsert by holding + record date)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DividendCalculationService {

    private final MarketDataResolver marketDataResolver;
    private final HoldingRepository holdingRepository;
    private final DividendRepository dividendRepository;
    private final TransactionService transactionService;

    private static final Set<String> BUY_TYPES = Set.of("BUY", "SIP", "LUMPSUM");
    private static final Set<String> SELL_TYPES = Set.of("SELL");
    private static final Set<AssetType> DIVIDEND_ASSET_TYPES = EnumSet.of(AssetType.EQUITY, AssetType.ETF);

    /**
     * Calculate dividends for all equity/ETF holdings of a user.
     * Returns count of new dividend records created.
     */
    @Transactional
    public Map<String, Object> calculateDividends(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId).stream()
                .filter(h -> DIVIDEND_ASSET_TYPES.contains(h.getAssetType()))
                .toList();

        List<TransactionResponse> allTransactions = transactionService.getUserTransactions(userId.toString());
        int totalNew = 0;
        int totalUpdated = 0;
        List<Map<String, Object>> processed = new ArrayList<>();

        for (Holding holding : holdings) {
            try {
                Map<String, Object> result = calculateForHolding(holding, allTransactions);
                totalNew += (int) result.getOrDefault("newDividends", 0);
                totalUpdated += (int) result.getOrDefault("updatedDividends", 0);
                if ((int) result.getOrDefault("newDividends", 0) > 0 || (int) result.getOrDefault("updatedDividends", 0) > 0) {
                    processed.add(result);
                }
            } catch (Exception e) {
                log.warn("Failed to calculate dividends for {}: {}", holding.getSymbol(), e.getMessage());
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("holdingsProcessed", holdings.size());
        summary.put("newDividends", totalNew);
        summary.put("updatedDividends", totalUpdated);
        summary.put("details", processed);
        return summary;
    }

    /**
     * Calculate dividends for a single holding.
     */
    @Transactional
    public Map<String, Object> calculateForHolding(Holding holding, List<TransactionResponse> allTransactions) {
        String symbol = holding.getSymbol();

        // 1. Fetch dividend events from NSE/Yahoo
        List<DividendEvent> events = marketDataResolver.getDividends(symbol);
        if (events.isEmpty()) {
            return Map.of("symbol", symbol, "events", 0, "newDividends", 0, "updatedDividends", 0);
        }

        // 2. Build transaction history for this holding to compute qty on each record date
        List<TransactionResponse> holdingTxns = allTransactions.stream()
                .filter(t -> t.getHoldingId().equals(holding.getId().toString()))
                .sorted(Comparator.comparing(t -> t.getTransactionDate().toLocalDate()))
                .toList();

        // 3. For each dividend event, compute qty held on record date and calculate payout
        int newCount = 0;
        int updatedCount = 0;
        BigDecimal totalDividendEarned = BigDecimal.ZERO;

        for (DividendEvent event : events) {
            LocalDate recordDate = event.getRecordDate() != null ? event.getRecordDate() : event.getExDate();
            if (recordDate == null) continue;
            if (event.getAmountPerShare() == null || event.getAmountPerShare().compareTo(BigDecimal.ZERO) <= 0) continue;

            // Compute quantity held on the record date
            BigDecimal qtyOnRecordDate = computeQtyOnDate(holdingTxns, recordDate);
            if (qtyOnRecordDate.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Calculate dividend payout
            BigDecimal payout = qtyOnRecordDate.multiply(event.getAmountPerShare()).setScale(2, RoundingMode.HALF_UP);
            totalDividendEarned = totalDividendEarned.add(payout);

            // Upsert: check if we already have a dividend record for this holding + record date
            Optional<Dividend> existing = dividendRepository.findByHoldingId(holding.getId()).stream()
                    .filter(d -> recordDate.equals(d.getRecordDate()) || recordDate.equals(d.getExDate()))
                    .findFirst();

            if (existing.isPresent()) {
                Dividend div = existing.get();
                if (div.getDividendAmount().compareTo(payout) != 0) {
                    div.setDividendAmount(payout);
                    dividendRepository.save(div);
                    updatedCount++;
                }
            } else {
                Dividend div = Dividend.builder()
                        .holdingId(holding.getId())
                        .symbol(symbol)
                        .dividendAmount(payout)
                        .dividendType(event.getDividendType())
                        .recordDate(recordDate)
                        .exDate(event.getExDate())
                        .reinvested(false)
                        .build();
                dividendRepository.save(div);
                newCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbol", symbol);
        result.put("events", events.size());
        result.put("newDividends", newCount);
        result.put("updatedDividends", updatedCount);
        result.put("totalDividendEarned", totalDividendEarned);
        return result;
    }

    /**
     * Compute the quantity of a holding held on a specific date,
     * by replaying transactions up to that date.
     */
    private BigDecimal computeQtyOnDate(List<TransactionResponse> transactions, LocalDate date) {
        BigDecimal qty = BigDecimal.ZERO;
        for (TransactionResponse t : transactions) {
            LocalDate txDate = t.getTransactionDate().toLocalDate();
            if (txDate.isAfter(date)) break; // transactions are sorted by date
            if (BUY_TYPES.contains(t.getTransactionType().name())) {
                qty = qty.add(t.getQuantity().abs());
            } else if (SELL_TYPES.contains(t.getTransactionType().name())) {
                qty = qty.subtract(t.getQuantity().abs());
            }
        }
        return qty.max(BigDecimal.ZERO);
    }

    /**
     * Get all dividends for a specific holding, ordered by record date.
     */
    public List<Dividend> getHoldingDividends(UUID holdingId) {
        return dividendRepository.findByHoldingId(holdingId).stream()
                .sorted(Comparator.comparing(d -> d.getRecordDate() != null ? d.getRecordDate() : d.getExDate(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Get total dividends earned for a holding.
     */
    public BigDecimal getTotalDividends(UUID holdingId) {
        return dividendRepository.findByHoldingId(holdingId).stream()
                .map(Dividend::getDividendAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
