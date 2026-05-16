package com.networth.service.tax;

import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeductionService {

    private static final BigDecimal LIMIT_80C = new BigDecimal("150000");
    private static final BigDecimal LIMIT_NPS_80CCD1B = new BigDecimal("50000");

    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> get80CUtilization(UUID userId, String financialYear) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        LocalDate fyStart = LocalDate.of(Integer.parseInt(financialYear.split("-")[0]), 4, 1);
        LocalDate fyEnd = LocalDate.of(Integer.parseInt(financialYear.split("-")[1]), 3, 31);

        BigDecimal epfContribution = BigDecimal.ZERO;
        BigDecimal ppfContribution = BigDecimal.ZERO;
        BigDecimal elssInvestment = BigDecimal.ZERO;
        BigDecimal npsContribution = BigDecimal.ZERO;
        BigDecimal otherContribution = BigDecimal.ZERO;

        for (Holding holding : holdings) {
            List<Transaction> contributions = transactionRepository
                    .findByHoldingId(holding.getId())
                    .stream()
                    .filter(t -> t.getTransactionType() == TransactionType.BUY
                            || t.getTransactionType() == TransactionType.SIP
                            || t.getTransactionType() == TransactionType.LUMPSUM)
                    .filter(t -> {
                        LocalDate txnDate = t.getTransactionDate().toLocalDate();
                        return !txnDate.isBefore(fyStart) && !txnDate.isAfter(fyEnd);
                    })
                    .toList();

            BigDecimal fyAmount = contributions.stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            switch (holding.getAssetType()) {
                case EPF -> epfContribution = epfContribution.add(fyAmount);
                case PPF -> ppfContribution = ppfContribution.add(fyAmount);
                case NPS -> npsContribution = npsContribution.add(fyAmount);
                case MUTUAL_FUND -> {
                    if (isELSS(holding)) {
                        elssInvestment = elssInvestment.add(fyAmount);
                    }
                }
                case BOND -> {
                    if (is80CBond(holding)) {
                        otherContribution = otherContribution.add(fyAmount);
                    }
                }
                default -> {}
            }
        }

        BigDecimal total80C = epfContribution.add(ppfContribution).add(elssInvestment).add(otherContribution);
        BigDecimal utilized = total80C.min(LIMIT_80C);
        BigDecimal remaining = LIMIT_80C.subtract(utilized).max(BigDecimal.ZERO);

        return Map.of(
                "financialYear", financialYear,
                "limit", LIMIT_80C,
                "utilized", utilized,
                "remaining", remaining.max(BigDecimal.ZERO),
                "utilizationPercentage", utilized.divide(LIMIT_80C, 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)),
                "breakdown", Map.of(
                        "EPF", epfContribution,
                        "PPF", ppfContribution,
                        "ELSS", elssInvestment,
                        "NPS", npsContribution,
                        "Other", otherContribution
                ),
                "nps80CCD1B", Map.of(
                        "contribution", npsContribution,
                        "limit", LIMIT_NPS_80CCD1B,
                        "additional", npsContribution.min(LIMIT_NPS_80CCD1B)
                )
        );
    }

    private boolean isELSS(Holding holding) {
        if (holding.getMetadata() == null) return false;
        Object elss = holding.getMetadata().get("elss");
        return elss != null && Boolean.parseBoolean(elss.toString());
    }

    private boolean is80CBond(Holding holding) {
        if (holding.getMetadata() == null) return false;
        Object eligible = holding.getMetadata().get("eligible80c");
        return eligible != null && Boolean.parseBoolean(eligible.toString());
    }
}
