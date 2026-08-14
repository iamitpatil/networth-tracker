package com.networth.service;

import com.networth.model.entity.Dividend;
import com.networth.model.entity.Holding;
import com.networth.model.entity.SymbolEvent;
import com.networth.model.enums.AssetType;
import com.networth.repository.DividendRepository;
import com.networth.repository.HoldingRepository;
import com.networth.repository.SymbolEventRepository;
import com.networth.service.market.SymbolEventService;
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
 * Calculates dividends earned per holding from the stored corporate-action events.
 *
 * <p>Reads {@code symbol_events} rather than calling a provider. That is the fix for dividends never
 * being calculated: this class used to call {@code marketDataResolver.getDividends(symbol)} once per
 * holding inside the loop below, and the resolver <em>skips</em> a rate-limited provider instead of
 * waiting. Correct on the live price path, fatal here — {@code nse} allows 1/s and 10/min and
 * {@code yahoo} 5/s, so a 24-holding loop drained both per-second buckets in its first 165ms and about
 * eighteen holdings had no request issued for them at all. Because the skip logs at debug, the endpoint
 * answered {@code newDividends: 0} and looked like it had worked.
 *
 * <p>{@code SymbolEventService} now fetches each symbol's whole history once, so this is a local join:
 * no provider call, no rate limit, nothing to skip, and every holding computed on every run.
 *
 * <p>The steps are otherwise unchanged: find the quantity held on each entitlement date by replaying
 * transactions, multiply by the amount per share, and upsert one {@link Dividend} per holding per date.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DividendCalculationService {

    private final HoldingRepository holdingRepository;
    private final DividendRepository dividendRepository;
    private final SymbolEventRepository symbolEventRepository;
    private final TransactionService transactionService;

    private static final Set<AssetType> DIVIDEND_ASSET_TYPES = EnumSet.of(AssetType.EQUITY, AssetType.ETF);

    /**
     * Transaction types that add shares to the register, and those that remove them.
     *
     * <p>Transfers are in both lists, which they were not before. A transfer is not a purchase and not a
     * disposal, but it absolutely changes who is on the register on a record date — shares moved in from
     * another demat account earn the dividend, and shares moved out do not. Omitting them understated
     * every payout on a transferred position.
     *
     * <p>Corporate actions are handled separately below, as signed deltas.
     */
    private static final Set<String> ADD_TYPES =
            Set.of("BUY", "SIP", "LUMPSUM", "TRANSFER_IN", "OPEN", "DEPOSIT", "CONTRIBUTION");
    private static final Set<String> REMOVE_TYPES = Set.of("SELL", "TRANSFER_OUT", "WITHDRAWAL", "WITHDRAW");

    /**
     * Calculate dividends for all equity/ETF holdings of a user.
     *
     * <p>Reports coverage as well as counts. A run before the event sync has happened used to return
     * {@code newDividends: 0} indistinguishably from "you are up to date"; {@code symbolsAwaitingSync}
     * says which it is.
     */
    @Transactional
    public Map<String, Object> calculateDividends(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId).stream()
                .filter(h -> DIVIDEND_ASSET_TYPES.contains(h.getAssetType()))
                .toList();

        if (holdings.isEmpty()) {
            return summary(0, 0, 0, 0, 0, List.of());
        }

        List<TransactionResponse> allTransactions = transactionService.getUserTransactions(userId.toString());

        // One query for every symbol in the portfolio, not one per holding. Asking per holding is the
        // shape that caused the original bug, and it is just as wasteful against the database.
        Set<String> symbols = holdings.stream()
                .map(Holding::getSymbol)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, List<SymbolEvent>> eventsBySymbol = symbols.isEmpty()
                ? Map.of()
                : symbolEventRepository
                        .findBySymbolsAndType(symbols, SymbolEventService.TYPE_DIVIDEND).stream()
                        .collect(Collectors.groupingBy(SymbolEvent::getSymbol));

        int totalNew = 0;
        int totalUpdated = 0;
        List<Map<String, Object>> processed = new ArrayList<>();

        for (Holding holding : holdings) {
            try {
                List<SymbolEvent> events = eventsBySymbol.getOrDefault(holding.getSymbol(), List.of());
                Map<String, Object> result = calculateForHolding(holding, allTransactions, events);
                int newCount = (int) result.getOrDefault("newDividends", 0);
                int updatedCount = (int) result.getOrDefault("updatedDividends", 0);
                totalNew += newCount;
                totalUpdated += updatedCount;
                if (newCount > 0 || updatedCount > 0) {
                    processed.add(result);
                }
            } catch (Exception e) {
                log.warn("Failed to calculate dividends for {}: {}", holding.getSymbol(), e.getMessage());
            }
        }

        long withEvents = symbols.stream().filter(eventsBySymbol::containsKey).count();
        return summary(holdings.size(), totalNew, totalUpdated,
                (int) withEvents, (int) (symbols.size() - withEvents), processed);
    }

    private Map<String, Object> summary(int holdingsProcessed, int newDividends, int updatedDividends,
                                        int symbolsWithEvents, int symbolsAwaitingSync,
                                        List<Map<String, Object>> details) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("holdingsProcessed", holdingsProcessed);
        summary.put("newDividends", newDividends);
        summary.put("updatedDividends", updatedDividends);
        summary.put("symbolsWithEvents", symbolsWithEvents);
        summary.put("symbolsAwaitingSync", symbolsAwaitingSync);
        if (symbolsAwaitingSync > 0) {
            // Says what to do about it, because there is nothing the user can fix by pressing Refresh
            // again -- the events genuinely are not fetched yet.
            summary.put("note", symbolsAwaitingSync + " symbol(s) have no stored corporate-action events yet. "
                    + "Run POST /api/v1/market/backfill to sync them. NSE allows ten requests a minute, so "
                    + "the first sync is slow: minutes at app.events.scope=held, hours at the default "
                    + "scope=all. It only happens once -- after that this is a local lookup.");
        }
        summary.put("details", details);
        return summary;
    }

    /**
     * Calculate dividends for a single holding from its symbol's events.
     */
    @Transactional
    public Map<String, Object> calculateForHolding(Holding holding,
                                                  List<TransactionResponse> allTransactions,
                                                  List<SymbolEvent> events) {
        String symbol = holding.getSymbol();
        if (events.isEmpty()) {
            return Map.of("symbol", symbol, "events", 0, "newDividends", 0, "updatedDividends", 0);
        }

        // Transaction history for this holding, to compute quantity on each entitlement date
        List<TransactionResponse> holdingTxns = allTransactions.stream()
                .filter(t -> t.getHoldingId().equals(holding.getId().toString()))
                .sorted(Comparator.comparing(t -> t.getTransactionDate().toLocalDate()))
                .toList();

        // Loaded once per holding. This used to run inside the event loop below, so a symbol with
        // twenty dividends issued twenty identical queries.
        Map<LocalDate, Dividend> existingByDate = new HashMap<>();
        for (Dividend d : dividendRepository.findByHoldingId(holding.getId())) {
            if (d.getRecordDate() != null) existingByDate.putIfAbsent(d.getRecordDate(), d);
            if (d.getExDate() != null) existingByDate.putIfAbsent(d.getExDate(), d);
        }

        int newCount = 0;
        int updatedCount = 0;
        BigDecimal totalDividendEarned = BigDecimal.ZERO;

        for (SymbolEvent event : events) {
            LocalDate entitlementDate = event.entitlementDate();
            if (entitlementDate == null) continue;
            BigDecimal perShare = event.getAmountPerShare();
            if (perShare == null || perShare.signum() <= 0) continue;

            BigDecimal qtyOnDate = computeQtyOnDate(holdingTxns, entitlementDate);
            if (qtyOnDate.signum() <= 0) continue;

            BigDecimal payout = qtyOnDate.multiply(perShare).setScale(2, RoundingMode.HALF_UP);
            totalDividendEarned = totalDividendEarned.add(payout);

            Dividend existing = existingByDate.get(entitlementDate);
            if (existing != null) {
                if (existing.getDividendAmount().compareTo(payout) != 0) {
                    existing.setDividendAmount(payout);
                    dividendRepository.save(existing);
                    updatedCount++;
                }
            } else {
                Dividend div = Dividend.builder()
                        .holdingId(holding.getId())
                        .symbol(symbol)
                        .dividendAmount(payout)
                        .dividendType(event.getEventSubtype() == null || event.getEventSubtype().isBlank()
                                ? "Dividend" : event.getEventSubtype())
                        .recordDate(event.getRecordDate())
                        .exDate(event.getExDate())
                        .reinvested(false)
                        .build();
                dividendRepository.save(div);
                // Kept in the map so a second event on the same date updates rather than inserting a
                // duplicate this same run.
                existingByDate.put(entitlementDate, div);
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
     * The quantity of a holding held on a specific date, by replaying transactions up to that date.
     *
     * <p>Corporate actions are applied as signed deltas rather than through {@code abs()}, matching
     * {@code InvestmentOverTimeService.getInvestmentOverTime}: the stored quantity is already signed —
     * positive for bonus shares and splits, negative for a consolidation, zero for the parent leg of a
     * demerger — so taking its absolute value would turn a consolidation into an increase.
     *
     * <p>This used to count only BUY/SIP/LUMPSUM and SELL, so a bonus issue, a share split or a transfer
     * between demat accounts left the quantity understated on every later record date, and the payout
     * with it.
     */
    private BigDecimal computeQtyOnDate(List<TransactionResponse> transactions, LocalDate date) {
        BigDecimal qty = BigDecimal.ZERO;
        for (TransactionResponse t : transactions) {
            LocalDate txDate = t.getTransactionDate().toLocalDate();
            if (txDate.isAfter(date)) break; // sorted by date

            BigDecimal signed = t.getQuantity() != null ? t.getQuantity() : BigDecimal.ZERO;
            String type = t.getTransactionType().name();

            if (t.getTransactionType().isCorporateAction()) {
                qty = qty.add(signed);
            } else if (ADD_TYPES.contains(type)) {
                qty = qty.add(signed.abs());
            } else if (REMOVE_TYPES.contains(type)) {
                qty = qty.subtract(signed.abs());
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
