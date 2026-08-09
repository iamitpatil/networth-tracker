package com.networth.service;

import com.networth.exception.AccessDeniedException;
import com.networth.exception.ResourceNotFoundException;
import com.networth.model.entity.Liability;
import com.networth.repository.LiabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EMIService {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final LiabilityRepository liabilityRepository;

    @Transactional(readOnly = true)
    public List<Liability> getUserLiabilities(UUID userId) {
        return liabilityRepository.findByUserId(userId);
    }

    @Transactional
    public Liability createLiability(UUID userId, LiabilityRequest request) {
        BigDecimal monthlyEmi = calculateEMI(request.originalAmount(), request.interestRate(), request.tenureMonths());
        LocalDate startDate = request.startDate() != null ? request.startDate() : LocalDate.now();
        LocalDate endDate = startDate.plusMonths(request.tenureMonths());

        Liability liability = Liability.builder()
                .userId(userId)
                .liabilityType(request.liabilityType())
                .lender(request.lender())
                .originalAmount(request.originalAmount())
                .outstandingAmount(request.originalAmount())
                .interestRate(request.interestRate())
                .monthlyEmi(monthlyEmi)
                .startDate(startDate)
                .endDate(endDate)
                .nextEmiDate(startDate.plusMonths(1))
                .build();

        return liabilityRepository.save(liability);
    }

    @Transactional(readOnly = true)
    public List<EMIScheduleEntry> generateEMISchedule(UUID userId, UUID liabilityId) {
        Liability liability = findOwnedLiability(userId, liabilityId);

        long totalMonths = java.time.temporal.ChronoUnit.MONTHS.between(liability.getStartDate(), liability.getEndDate());
        // Monthly rate: annualRate / 12 / 100 (using BigDecimal precision)
        BigDecimal monthlyRate = liability.getInterestRate()
                .divide(TWELVE, MC)
                .divide(HUNDRED, MC);
        BigDecimal outstanding = liability.getOriginalAmount();

        List<EMIScheduleEntry> schedule = new ArrayList<>();
        LocalDate currentDate = liability.getStartDate().plusMonths(1);

        for (int month = 1; month <= totalMonths; month++) {
            BigDecimal interestComponent = outstanding.multiply(monthlyRate, MC);
            BigDecimal principalComponent = liability.getMonthlyEmi().subtract(interestComponent);
            outstanding = outstanding.subtract(principalComponent);

            if (outstanding.compareTo(BigDecimal.ZERO) < 0) {
                outstanding = BigDecimal.ZERO;
            }

            schedule.add(EMIScheduleEntry.builder()
                    .emiNumber(month)
                    .dueDate(currentDate)
                    .emiAmount(liability.getMonthlyEmi())
                    .principalComponent(principalComponent.setScale(2, RoundingMode.HALF_UP))
                    .interestComponent(interestComponent.setScale(2, RoundingMode.HALF_UP))
                    .outstandingBalance(outstanding.setScale(2, RoundingMode.HALF_UP))
                    .isPaid(false)
                    .build());

            currentDate = currentDate.plusMonths(1);
        }

        return schedule;
    }

    @Transactional
    public Liability markEMIPaid(UUID userId, UUID liabilityId, LocalDate paymentDate, BigDecimal amount) {
        Liability liability = findOwnedLiability(userId, liabilityId);

        BigDecimal deductAmount = amount != null ? amount : liability.getMonthlyEmi();
        BigDecimal newOutstanding = liability.getOutstandingAmount().subtract(deductAmount);
        liability.setOutstandingAmount(newOutstanding.max(BigDecimal.ZERO));
        if (liability.getNextEmiDate() != null) {
            liability.setNextEmiDate(liability.getNextEmiDate().plusMonths(1));
        }

        return liabilityRepository.save(liability);
    }

    @Transactional
    public void deleteLiability(UUID userId, UUID liabilityId) {
        Liability liability = findOwnedLiability(userId, liabilityId);
        // Soft delete: preserve for audit
        liability.setDeletedAt(java.time.Instant.now());
        liabilityRepository.save(liability);
        log.info("Soft-deleted liability {} for user {}", liabilityId, userId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLoanSummary(UUID userId, UUID liabilityId) {
        Liability liability = findOwnedLiability(userId, liabilityId);

        long totalMonths = java.time.temporal.ChronoUnit.MONTHS.between(liability.getStartDate(), liability.getEndDate());
        long elapsedMonths = java.time.temporal.ChronoUnit.MONTHS.between(liability.getStartDate(), LocalDate.now());
        long remainingMonths = Math.max(0, totalMonths - elapsedMonths);

        BigDecimal totalPayable = liability.getMonthlyEmi().multiply(BigDecimal.valueOf(totalMonths));
        BigDecimal totalInterest = totalPayable.subtract(liability.getOriginalAmount());
        BigDecimal paidAmount = liability.getOriginalAmount().subtract(liability.getOutstandingAmount());
        BigDecimal paidInterest = BigDecimal.ZERO;
        if (totalMonths > 0) {
            paidInterest = totalInterest.multiply(
                    BigDecimal.valueOf(elapsedMonths).divide(BigDecimal.valueOf(totalMonths), 4, RoundingMode.HALF_UP));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("originalAmount", liability.getOriginalAmount());
        summary.put("outstandingAmount", liability.getOutstandingAmount());
        summary.put("monthlyEmi", liability.getMonthlyEmi());
        summary.put("interestRate", liability.getInterestRate());
        summary.put("totalPayable", totalPayable);
        summary.put("totalInterest", totalInterest);
        summary.put("paidAmount", paidAmount);
        summary.put("paidInterest", paidInterest);

        BigDecimal progressPct = BigDecimal.ZERO;
        if (liability.getOriginalAmount().compareTo(BigDecimal.ZERO) > 0) {
            progressPct = paidAmount.divide(liability.getOriginalAmount(), 4, RoundingMode.HALF_UP)
                    .multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
        }
        summary.put("progressPercentage", progressPct);
        summary.put("remainingMonths", remainingMonths);
        summary.put("nextEmiDate", liability.getNextEmiDate());
        return summary;
    }

    /**
     * Calculate EMI using BigDecimal precision (no double float errors).
     * Formula: EMI = P * r * (1+r)^n / ((1+r)^n - 1)
     * Where: P = principal, r = monthly rate, n = number of months
     */
    public BigDecimal calculateEMI(BigDecimal principal, BigDecimal annualRate, long tenureMonths) {
        if (tenureMonths <= 0) {
            throw new IllegalArgumentException("Tenure must be positive");
        }
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Principal must be positive");
        }
        if (annualRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Interest rate cannot be negative");
        }

        // Handle zero interest case
        if (annualRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(tenureMonths), 2, RoundingMode.HALF_UP);
        }

        BigDecimal monthlyRate = annualRate.divide(TWELVE, MC).divide(HUNDRED, MC);
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        // (1+r)^n
        BigDecimal onePlusRPowerN = onePlusR.pow((int) tenureMonths, MC);
        // P * r * (1+r)^n
        BigDecimal numerator = principal.multiply(monthlyRate, MC).multiply(onePlusRPowerN, MC);
        // (1+r)^n - 1
        BigDecimal denominator = onePlusRPowerN.subtract(BigDecimal.ONE);

        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    /**
     * Find a liability ensuring it belongs to the given user.
     */
    private Liability findOwnedLiability(UUID userId, UUID liabilityId) {
        Liability liability = liabilityRepository.findById(liabilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Liability", liabilityId.toString()));
        if (!liability.getUserId().equals(userId)) {
            log.warn("User {} attempted to access liability {} owned by {}", userId, liabilityId, liability.getUserId());
            throw new AccessDeniedException("Liability", liabilityId.toString());
        }
        return liability;
    }

    @lombok.Builder
    @lombok.Getter
    public static class EMIScheduleEntry {
        private int emiNumber;
        private LocalDate dueDate;
        private BigDecimal emiAmount;
        private BigDecimal principalComponent;
        private BigDecimal interestComponent;
        private BigDecimal outstandingBalance;
        private boolean isPaid;
    }

    public record LiabilityRequest(
            String liabilityType,
            String lender,
            BigDecimal originalAmount,
            BigDecimal interestRate,
            int tenureMonths,
            LocalDate startDate
    ) {}
}
