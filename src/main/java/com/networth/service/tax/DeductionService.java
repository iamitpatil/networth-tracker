package com.networth.service.tax;

import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.TransactionRepository;
import com.networth.service.tax.rules.DeductionLimits;
import com.networth.service.tax.rules.TaxRuleRegistry;
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

    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    /** Deduction ceilings per financial year. */
    private final TaxRuleRegistry ruleRegistry;

    @Transactional(readOnly = true)
    public Map<String, Object> get80CUtilization(UUID userId, String financialYear) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        // Derived via the registry: parsing the second component directly turned "2024-25"
        // into the year 25 AD, silently excluding every transaction from the window.
        DeductionLimits limits = ruleRegistry.forFinancialYear(financialYear).deductions();
        LocalDate fyStart = ruleRegistry.startOf(financialYear);
        LocalDate fyEnd = ruleRegistry.endOf(financialYear);

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
        BigDecimal utilized = total80C.min(limits.limit80C());
        BigDecimal remaining = limits.limit80C().subtract(utilized).max(BigDecimal.ZERO);

        return Map.of(
                "financialYear", financialYear,
                "limit", limits.limit80C(),
                "utilized", utilized,
                "remaining", remaining.max(BigDecimal.ZERO),
                "utilizationPercentage", utilized.divide(limits.limit80C(), 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)),
                "breakdown", Map.of(
                        "EPF", epfContribution,
                        "PPF", ppfContribution,
                        "ELSS", elssInvestment,
                        "NPS", npsContribution,
                        "Other", otherContribution
                ),
                "nps80CCD1B", Map.of(
                        "contribution", npsContribution,
                        "limit", limits.limit80CCD1B(),
                        "additional", npsContribution.min(limits.limit80CCD1B())
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
