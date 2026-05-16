package com.networth.service.analytics;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RiskService {

    public BigDecimal calculateVolatility(List<Double> returns) {
        if (returns == null || returns.size() < 2) {
            return BigDecimal.ZERO;
        }

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .average()
                .orElse(0);

        double dailyVolatility = Math.sqrt(variance);
        double annualizedVolatility = dailyVolatility * Math.sqrt(252);

        return BigDecimal.valueOf(annualizedVolatility * 100).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateSharpeRatio(List<Double> returns, double riskFreeRate) {
        if (returns == null || returns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        double meanReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double annualizedReturn = meanReturn * 252;

        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - meanReturn, 2))
                .average()
                .orElse(0);

        double dailyVolatility = Math.sqrt(variance);
        double annualizedVolatility = dailyVolatility * Math.sqrt(252);

        if (annualizedVolatility == 0) {
            return BigDecimal.ZERO;
        }

        double sharpe = (annualizedReturn - riskFreeRate) / annualizedVolatility;
        return BigDecimal.valueOf(sharpe).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateMaxDrawdown(List<Double> returns) {
        if (returns == null || returns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        double peak = 1.0;
        double maxDrawdown = 0;
        double currentValue = 1.0;

        for (Double ret : returns) {
            currentValue *= (1 + ret);
            if (currentValue > peak) {
                peak = currentValue;
            }
            double drawdown = (peak - currentValue) / peak;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }

        return BigDecimal.valueOf(maxDrawdown * 100).setScale(2, RoundingMode.HALF_UP);
    }
}
