package com.networth.service;

import com.networth.model.entity.Dividend;
import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.repository.DividendRepository;
import com.networth.repository.HoldingRepository;
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
public class DividendService {

    private final DividendRepository dividendRepository;
    private final HoldingRepository holdingRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getDividendSummary(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);

        BigDecimal totalDividends = BigDecimal.ZERO;
        Map<String, BigDecimal> dividendsByYear = new TreeMap<>(Collections.reverseOrder());
        Map<String, BigDecimal> dividendsByStock = new LinkedHashMap<>();

        for (Holding holding : holdings) {
            List<Dividend> dividends = dividendRepository.findByHoldingId(holding.getId());

            for (Dividend dividend : dividends) {
                totalDividends = totalDividends.add(dividend.getDividendAmount());

                String year = dividend.getPaymentDate() != null
                        ? String.valueOf(dividend.getPaymentDate().getYear())
                        : dividend.getRecordDate() != null
                        ? String.valueOf(dividend.getRecordDate().getYear())
                        : dividend.getExDate() != null
                        ? String.valueOf(dividend.getExDate().getYear())
                        : "unknown";
                dividendsByYear.merge(year, dividend.getDividendAmount(), BigDecimal::add);

                dividendsByStock.merge(holding.getSymbol(), dividend.getDividendAmount(), BigDecimal::add);
            }
        }

        BigDecimal currentValue = holdings.stream()
                .filter(h -> h.getAssetType() == AssetType.EQUITY || h.getAssetType() == AssetType.ETF)
                .map(h -> h.getCurrentValue() != null ? h.getCurrentValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal dividendYield = currentValue.compareTo(BigDecimal.ZERO) > 0
                ? totalDividends.divide(currentValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return Map.of(
                "totalDividends", totalDividends,
                "dividendYield", dividendYield.setScale(2, RoundingMode.HALF_UP),
                "dividendsByYear", dividendsByYear,
                "dividendsByStock", dividendsByStock,
                "topPayers", dividendsByStock.entrySet().stream()
                        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                        .limit(5)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (e1, e2) -> e1,
                                LinkedHashMap::new
                        ))
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPassiveIncomeEstimate(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);

        BigDecimal monthlyDividend = BigDecimal.ZERO;
        BigDecimal monthlyInterest = BigDecimal.ZERO;
        BigDecimal monthlyRent = BigDecimal.ZERO;

        for (Holding holding : holdings) {
            List<Dividend> dividends = dividendRepository.findByHoldingId(holding.getId());
            BigDecimal annualDividend = dividends.stream()
                    .map(Dividend::getDividendAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            monthlyDividend = monthlyDividend.add(annualDividend.divide(BigDecimal.valueOf(12), RoundingMode.HALF_UP));

            if (holding.getAssetType() == AssetType.FD || holding.getAssetType() == AssetType.BOND) {
                BigDecimal annualInterest = holding.getCurrentValue() != null
                        ? holding.getCurrentValue().multiply(holding.getMetadata() != null
                                ? new BigDecimal(holding.getMetadata().getOrDefault("interestRate", "0").toString())
                                : BigDecimal.ZERO)
                        .divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                monthlyInterest = monthlyInterest.add(annualInterest.divide(BigDecimal.valueOf(12), RoundingMode.HALF_UP));
            }

            if (holding.getAssetType() == AssetType.REAL_ESTATE) {
                BigDecimal monthlyRentAmount = holding.getMetadata() != null
                        ? new BigDecimal(holding.getMetadata().getOrDefault("monthlyRent", "0").toString())
                        : BigDecimal.ZERO;
                monthlyRent = monthlyRent.add(monthlyRentAmount);
            }
        }

        BigDecimal totalMonthlyIncome = monthlyDividend.add(monthlyInterest).add(monthlyRent);

        return Map.of(
                "monthlyIncome", totalMonthlyIncome.setScale(2, RoundingMode.HALF_UP),
                "breakdown", Map.of(
                        "dividends", monthlyDividend.setScale(2, RoundingMode.HALF_UP),
                        "interest", monthlyInterest.setScale(2, RoundingMode.HALF_UP),
                        "rent", monthlyRent.setScale(2, RoundingMode.HALF_UP)
                ),
                "annualIncome", totalMonthlyIncome.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP)
        );
    }
}
