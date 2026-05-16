package com.networth.service.tax;

import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CapitalGainsCalculator {

    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> calculateCapitalGains(UUID userId, String financialYear) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        LocalDate fyStart = parseFinancialYearStart(financialYear);
        LocalDate fyEnd = parseFinancialYearEnd(financialYear);

        List<CapitalGain> equityGains = new ArrayList<>();
        List<CapitalGain> debtGains = new ArrayList<>();
        List<CapitalGain> goldGains = new ArrayList<>();
        List<CapitalGain> cryptoGains = new ArrayList<>();
        List<CapitalGain> realEstateGains = new ArrayList<>();

        for (Holding holding : holdings) {
            List<Transaction> sells = transactionRepository.findByHoldingId(holding.getId())
                    .stream()
                    .filter(t -> t.getTransactionType() == TransactionType.SELL)
                    .filter(t -> {
                        LocalDate txnDate = t.getTransactionDate().toLocalDate();
                        return !txnDate.isBefore(fyStart) && !txnDate.isAfter(fyEnd);
                    })
                    .toList();

            List<Transaction> buys = transactionRepository.findByHoldingId(holding.getId())
                    .stream()
                    .filter(t -> t.getTransactionType() == TransactionType.BUY
                            || t.getTransactionType() == TransactionType.SIP
                            || t.getTransactionType() == TransactionType.LUMPSUM)
                    .sorted(Comparator.comparing(Transaction::getTransactionDate))
                    .toList();

            Queue<Transaction> buyQueue = new LinkedList<>(buys);

            for (Transaction sell : sells) {
                double remainingQty = sell.getQuantity().abs().doubleValue();

                while (remainingQty > 0 && !buyQueue.isEmpty()) {
                    Transaction buy = buyQueue.peek();
                    double availableQty = buy.getQuantity().doubleValue();

                    double sellQty = Math.min(remainingQty, availableQty);
                    double costBasis = sellQty * buy.getPrice().doubleValue();
                    double saleValue = sellQty * sell.getPrice().doubleValue();
                    double gain = saleValue - costBasis;

                    long holdingDays = java.time.temporal.ChronoUnit.DAYS.between(
                            buy.getTransactionDate().toLocalDate(), sell.getTransactionDate().toLocalDate());

                    CapitalGain capitalGain = CapitalGain.builder()
                            .holdingId(holding.getId().toString())
                            .symbol(holding.getSymbol())
                            .assetType(holding.getAssetType())
                            .saleDate(sell.getTransactionDate().toLocalDate())
                            .purchaseDate(buy.getTransactionDate().toLocalDate())
                            .quantity(BigDecimal.valueOf(sellQty))
                            .salePrice(sell.getPrice())
                            .purchasePrice(buy.getPrice())
                            .costBasis(BigDecimal.valueOf(costBasis))
                            .saleProceeds(BigDecimal.valueOf(saleValue))
                            .gain(BigDecimal.valueOf(gain))
                            .holdingDays(holdingDays)
                            .isLongTerm(isLongTerm(holding.getAssetType(), holdingDays))
                            .build();

                    if (gain > 0) {
                        switch (holding.getAssetType()) {
                            case EQUITY, ETF -> equityGains.add(capitalGain);
                            case MUTUAL_FUND -> {
                                if (isEquityOrientedMF(holding)) {
                                    equityGains.add(capitalGain);
                                } else {
                                    debtGains.add(capitalGain);
                                }
                            }
                            case GOLD, SGB -> goldGains.add(capitalGain);
                            case CRYPTO -> cryptoGains.add(capitalGain);
                            case REAL_ESTATE -> realEstateGains.add(capitalGain);
                            default -> debtGains.add(capitalGain);
                        }
                    }

                    remainingQty -= sellQty;
                    if (sellQty >= availableQty) {
                        buyQueue.poll();
                    }
                }
            }
        }

        BigDecimal totalEquityLTCG = sumGains(equityGains, true);
        BigDecimal totalEquitySTCG = sumGains(equityGains, false);
        BigDecimal totalDebtGains = debtGains.stream()
                .map(CapitalGain::getGain)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGoldGains = goldGains.stream()
                .map(CapitalGain::getGain)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCryptoGains = cryptoGains.stream()
                .map(CapitalGain::getGain)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRealEstateGains = realEstateGains.stream()
                .map(CapitalGain::getGain)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal exemptionLimit = new BigDecimal("125000");
        BigDecimal taxableEquityLTCG = totalEquityLTCG.compareTo(exemptionLimit) > 0
                ? totalEquityLTCG.subtract(exemptionLimit)
                : BigDecimal.ZERO;

        BigDecimal taxOnEquityLTCG = taxableEquityLTCG.multiply(new BigDecimal("0.125"));
        BigDecimal taxOnEquitySTCG = totalEquitySTCG.multiply(new BigDecimal("0.20"));
        BigDecimal taxOnCrypto = totalCryptoGains.multiply(new BigDecimal("0.30"));
        BigDecimal cess = taxOnEquityLTCG.add(taxOnEquitySTCG).add(taxOnCrypto)
                .multiply(new BigDecimal("0.04"));

        return Map.of(
                "financialYear", financialYear,
                "equity", Map.of(
                        "ltcg", totalEquityLTCG,
                        "stcg", totalEquitySTCG,
                        "taxableLTCG", taxableEquityLTCG,
                        "taxOnLTCG", taxOnEquityLTCG,
                        "taxOnSTCG", taxOnEquitySTCG
                ),
                "debt", Map.of(
                        "gains", totalDebtGains
                ),
                "gold", Map.of(
                        "gains", totalGoldGains
                ),
                "crypto", Map.of(
                        "gains", totalCryptoGains,
                        "tax", taxOnCrypto
                ),
                "realEstate", Map.of(
                        "gains", totalRealEstateGains
                ),
                "totalTax", taxOnEquityLTCG.add(taxOnEquitySTCG).add(taxOnCrypto).add(cess),
                "cess", cess
        );
    }

    private boolean isLongTerm(AssetType assetType, long holdingDays) {
        return switch (assetType) {
            case EQUITY, ETF, MUTUAL_FUND -> holdingDays >= 365;
            case GOLD, SGB, REAL_ESTATE, FD, BOND -> holdingDays >= 1095;
            case CRYPTO -> true;
            default -> holdingDays >= 730;
        };
    }

    private boolean isEquityOrientedMF(Holding holding) {
        if (holding.getMetadata() == null) return true;
        Object equity = holding.getMetadata().get("equityOrientation");
        if (equity == null) return true;
        return Boolean.parseBoolean(equity.toString());
    }

    private LocalDate parseFinancialYearStart(String fy) {
        String year = fy.split("-")[0];
        return LocalDate.of(Integer.parseInt(year), 4, 1);
    }

    private LocalDate parseFinancialYearEnd(String fy) {
        String year = fy.split("-")[1];
        return LocalDate.of(Integer.parseInt(year), 3, 31);
    }

    private BigDecimal sumGains(List<CapitalGain> gains, boolean longTerm) {
        return gains.stream()
                .filter(g -> g.isLongTerm() == longTerm)
                .map(CapitalGain::getGain)
                .filter(g -> g.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @lombok.Builder
    @lombok.Getter
    public static class CapitalGain {
        private String holdingId;
        private String symbol;
        private AssetType assetType;
        private LocalDate saleDate;
        private LocalDate purchaseDate;
        private BigDecimal quantity;
        private BigDecimal salePrice;
        private BigDecimal purchasePrice;
        private BigDecimal costBasis;
        private BigDecimal saleProceeds;
        private BigDecimal gain;
        private long holdingDays;
        private boolean isLongTerm;
    }
}
