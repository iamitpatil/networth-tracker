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
    private final TransactionRepository transactionRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final XIRRCalculator xirrCalculator;
    private final RiskService riskService;

    @Transactional(readOnly = true)
    public BigDecimal calculateXIRR(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        List<XIRRCalculator.CashFlow> allCashFlows = new ArrayList<>();

        for (Holding holding : holdings) {
            List<Transaction> txns = transactionRepository.findByHoldingIdOrderByTransactionDateDesc(holding.getId());

            for (Transaction txn : txns) {
                if (txn.getTransactionType() == TransactionType.BUY
                        || txn.getTransactionType() == TransactionType.SIP
                        || txn.getTransactionType() == TransactionType.LUMPSUM) {
                    allCashFlows.add(new XIRRCalculator.CashFlow(
                            txn.getTransactionDate().toLocalDate(),
                            txn.getAmount().negate().doubleValue()));
                } else if (txn.getTransactionType() == TransactionType.SELL) {
                    allCashFlows.add(new XIRRCalculator.CashFlow(
                            txn.getTransactionDate().toLocalDate(),
                            txn.getAmount().doubleValue()));
                }
            }

            if (holding.getCurrentValue() != null && holding.getCurrentValue().compareTo(BigDecimal.ZERO) > 0) {
                allCashFlows.add(new XIRRCalculator.CashFlow(
                        LocalDate.now(),
                        holding.getCurrentValue().doubleValue()));
            }
        }

        if (allCashFlows.isEmpty()) {
            return BigDecimal.ZERO;
        }

        allCashFlows.sort(Comparator.comparing(XIRRCalculator.CashFlow::date));
        return xirrCalculator.calculateXIRR(allCashFlows);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateCAGR(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);

        if (holdings.isEmpty()) return BigDecimal.ZERO;

        LocalDate earliestDate = null;
        LocalDate latestDate = null;
        BigDecimal initialInvestment = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;

        for (Holding holding : holdings) {
            List<Transaction> txns = transactionRepository.findByHoldingIdOrderByTransactionDateDesc(holding.getId());
            if (txns.isEmpty()) continue;

            Transaction firstTxn = txns.getLast();
            LocalDate txnDate = firstTxn.getTransactionDate().toLocalDate();

            if (earliestDate == null || txnDate.isBefore(earliestDate)) {
                earliestDate = txnDate;
            }

            if (latestDate == null || txnDate.isAfter(latestDate)) {
                latestDate = txnDate;
            }

            initialInvestment = initialInvestment.add(firstTxn.getAmount());
            if (holding.getCurrentValue() != null) {
                currentValue = currentValue.add(holding.getCurrentValue());
            }
        }

        if (earliestDate == null || initialInvestment.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        long days = java.time.temporal.ChronoUnit.DAYS.between(earliestDate, latestDate);
        if (days <= 0) return BigDecimal.ZERO;

        double years = days / 365.0;
        double cagr = Math.pow(currentValue.doubleValue() / initialInvestment.doubleValue(), 1.0 / years) - 1;

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

        for (Holding holding : holdings) {
            if (holding.getCurrentValue() != null) {
                totalValue = totalValue.add(holding.getCurrentValue());
            }

            List<MarketPrice> prices = marketPriceRepository
                    .findBySymbolAndAssetTypeOrderByPriceDateDesc(holding.getSymbol(), holding.getAssetType());

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
