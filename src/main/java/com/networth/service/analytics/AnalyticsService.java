package com.networth.service.analytics;

import com.networth.model.dto.HoldingResponse;
import com.networth.model.dto.PortfolioSummary;
import com.networth.model.entity.Holding;
import com.networth.model.entity.MarketPrice;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.MarketPriceRepository;
import com.networth.repository.TransactionRepository;
import com.networth.service.portfolio.HoldingService;
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
public class AnalyticsService {

    private final HoldingRepository holdingRepository;
    private final HoldingService holdingService;
    private final TransactionRepository transactionRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final XIRRCalculator xirrCalculator;
    private final RiskService riskService;

    /**
     * Portfolio XIRR, or {@code null} when it cannot be determined — no transactions, or
     * cash flows that no rate can reconcile. Null means "unknown", which is deliberately
     * distinct from a genuine 0% return.
     */
    /**
     * All of a user's transactions grouped by holding, newest first within each group.
     *
     * <p>One query instead of one per holding. The per-holding calls this replaces were an
     * N+1: a portfolio of 60 holdings issued 61 queries to compute a single figure, which the
     * Redis cache hid until the first cold read.
     */
    private Map<UUID, List<Transaction>> transactionsByHolding(UUID userId) {
        return transactionRepository.findByUserId(userId).stream()
                .filter(t -> t.getHoldingId() != null && t.getTransactionDate() != null)
                .sorted(Comparator.comparing(Transaction::getTransactionDate).reversed())
                .collect(Collectors.groupingBy(Transaction::getHoldingId));
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateXIRR(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        List<XIRRCalculator.CashFlow> allCashFlows = new ArrayList<>();

        Map<UUID, List<Transaction>> byHolding = transactionsByHolding(userId);

        for (Holding holding : holdings) {
            List<Transaction> txns = byHolding.getOrDefault(holding.getId(), List.of());

            for (Transaction txn : txns) {
                // Only rows that actually moved money. Bonus shares, splits and demerger legs
                // are position changes with no cash behind them, and a demerger leg carries a
                // non-zero amount — the apportioned cost — so it would read as a real purchase
                // and depress the return of a portfolio that had simply been reorganised.
                if (!txn.getTransactionType().isCashFlow() || txn.getAmount() == null) {
                    continue;
                }
                double amount = txn.getTransactionType().isOutflow()
                        ? txn.getAmount().negate().doubleValue()
                        : txn.getAmount().doubleValue();
                allCashFlows.add(new XIRRCalculator.CashFlow(
                        txn.getTransactionDate().toLocalDate(), amount));
            }

            if (holding.getCurrentValue() != null && holding.getCurrentValue().compareTo(BigDecimal.ZERO) > 0) {
                allCashFlows.add(new XIRRCalculator.CashFlow(
                        LocalDate.now(),
                        holding.getCurrentValue().doubleValue()));
            }
        }

        if (allCashFlows.isEmpty()) {
            return null;
        }

        allCashFlows.sort(Comparator.comparing(XIRRCalculator.CashFlow::date));
        return xirrCalculator.calculateXIRR(allCashFlows).orElse(null);
    }

    /**
     * Portfolio CAGR: the single annual rate that takes total money invested to today's value.
     *
     * <p>{@link #calculateXIRR(UUID)} is the better measure when contributions are irregular,
     * because CAGR cannot express when each rupee went in. CAGR is kept because it is the figure
     * people recognise, and it is computed here over the whole invested base rather than a
     * sample of it.
     *
     * <p>Three things were wrong before and are worth naming, because each inflated the number:
     * <ul>
     *   <li>only each holding's <em>first</em> transaction counted as invested capital, so every
     *       later purchase was free growth;</li>
     *   <li>the end date was the newest holding's first purchase rather than today, shortening
     *       the period and so raising the implied annual rate;</li>
     *   <li>sales were ignored, leaving money counted as still invested after it came back.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateCAGR(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);

        if (holdings.isEmpty()) return BigDecimal.ZERO;

        LocalDate earliestDate = null;
        BigDecimal netInvested = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;

        Map<UUID, List<Transaction>> byHolding = transactionsByHolding(userId);

        for (Holding holding : holdings) {
            for (Transaction txn : byHolding.getOrDefault(holding.getId(), List.of())) {
                TransactionType type = txn.getTransactionType();
                if (!type.isCashFlow() || txn.getAmount() == null) {
                    continue;   // corporate actions and transfers move no money
                }
                netInvested = type.isOutflow()
                        ? netInvested.add(txn.getAmount())
                        : netInvested.subtract(txn.getAmount());

                LocalDate txnDate = txn.getTransactionDate().toLocalDate();
                if (earliestDate == null || txnDate.isBefore(earliestDate)) {
                    earliestDate = txnDate;
                }
            }
            if (holding.getCurrentValue() != null) {
                currentValue = currentValue.add(holding.getCurrentValue());
            }
        }

        // Net invested can go to zero or negative once more has been withdrawn than put in.
        // There is no meaningful growth multiple against a zero or negative base, and raising a
        // negative ratio to a fractional power gives NaN, so say nothing rather than something
        // false.
        if (earliestDate == null || netInvested.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        long days = java.time.temporal.ChronoUnit.DAYS.between(earliestDate, LocalDate.now());
        if (days <= 0) return BigDecimal.ZERO;

        double years = days / 365.0;
        double cagr = Math.pow(currentValue.doubleValue() / netInvested.doubleValue(), 1.0 / years) - 1;
        if (!Double.isFinite(cagr)) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(cagr).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAssetAllocation(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);

        Map<AssetType, BigDecimal> allocation = new EnumMap<>(AssetType.class);
        BigDecimal totalValue = BigDecimal.ZERO;

        for (Holding holding : holdings) {
            BigDecimal value = holding.getCurrentValue() != null ? holding.getCurrentValue() : BigDecimal.ZERO;
            allocation.merge(holding.getAssetType(), value, BigDecimal::add);
            totalValue = totalValue.add(value);
        }

        Map<String, BigDecimal> percentageAllocation = new LinkedHashMap<>();
        for (Map.Entry<AssetType, BigDecimal> entry : allocation.entrySet()) {
            BigDecimal percentage = totalValue.compareTo(BigDecimal.ZERO) > 0
                    ? entry.getValue().divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            percentageAllocation.put(entry.getKey().toString(), percentage.setScale(2, RoundingMode.HALF_UP));
        }

        return Map.of(
                "totalValue", totalValue,
                "allocation", percentageAllocation,
                "holdingsCount", holdings.size()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSectorAllocation(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);

        Map<String, BigDecimal> sectorValues = new HashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        for (Holding holding : holdings) {
            BigDecimal value = holding.getCurrentValue() != null ? holding.getCurrentValue() : BigDecimal.ZERO;
            String sector = holding.getSector() != null ? holding.getSector() : "Unknown";
            sectorValues.merge(sector, value, BigDecimal::add);
            totalValue = totalValue.add(value);
        }

        Map<String, BigDecimal> percentages = new LinkedHashMap<>();
        final BigDecimal finalTotalValue = totalValue;
        sectorValues.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(entry -> {
                    BigDecimal percentage = finalTotalValue.compareTo(BigDecimal.ZERO) > 0
                            ? entry.getValue().divide(finalTotalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;
                    percentages.put(entry.getKey(), percentage.setScale(2, RoundingMode.HALF_UP));
                });

        return Map.of("sectors", percentages);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRiskMetrics(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);

        if (holdings.isEmpty()) {
            return Map.of();
        }

        BigDecimal totalValue = BigDecimal.ZERO;
        List<Double> dailyReturns = new ArrayList<>();

        // One price query for the whole portfolio rather than one per holding.
        Map<String, String> pricingSymbols = new HashMap<>();
        for (Holding holding : holdings) {
            pricingSymbols.put(holding.getId().toString(), holdingService.getEffectiveSymbolForPricing(holding));
        }
        Map<String, List<MarketPrice>> pricesBySymbolAndType = pricingSymbols.values().isEmpty()
                ? Map.of()
                : marketPriceRepository.findBySymbolInOrderByPriceDateDesc(new HashSet<>(pricingSymbols.values()))
                        .stream()
                        .collect(Collectors.groupingBy(mp -> mp.getSymbol() + "|" + mp.getAssetType()));

        for (Holding holding : holdings) {
            if (holding.getCurrentValue() != null) {
                totalValue = totalValue.add(holding.getCurrentValue());
            }

            String pricingSymbol = pricingSymbols.get(holding.getId().toString());
            List<MarketPrice> prices = pricesBySymbolAndType
                    .getOrDefault(pricingSymbol + "|" + holding.getAssetType(), List.of());

            if (prices.size() >= 2) {
                for (int i = 0; i < prices.size() - 1; i++) {
                    BigDecimal current = prices.get(i).getPrice();
                    BigDecimal previous = prices.get(i + 1).getPrice();
                    if (previous.compareTo(BigDecimal.ZERO) > 0) {
                        double returnVal = current.subtract(previous).divide(previous, 6, RoundingMode.HALF_UP).doubleValue();
                        dailyReturns.add(returnVal);
                    }
                }
            }
        }

        return Map.of(
                "volatility", riskService.calculateVolatility(dailyReturns),
                "sharpeRatio", riskService.calculateSharpeRatio(dailyReturns, 0.065),
                "maxDrawdown", riskService.calculateMaxDrawdown(dailyReturns),
                "totalValue", totalValue
        );
    }
}
