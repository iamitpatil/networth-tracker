package com.networth.service;

import com.networth.model.entity.Liability;
import com.networth.repository.LiabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EMIService {

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
    public List<EMIScheduleEntry> generateEMISchedule(UUID liabilityId) {
        Liability liability = liabilityRepository.findById(liabilityId)
                .orElseThrow(() -> new IllegalArgumentException("Liability not found"));

        long totalMonths = java.time.temporal.ChronoUnit.MONTHS.between(liability.getStartDate(), liability.getEndDate());
        double monthlyRate = liability.getInterestRate().doubleValue() / 12 / 100;
        double outstanding = liability.getOriginalAmount().doubleValue();

        List<EMIScheduleEntry> schedule = new ArrayList<>();
        LocalDate currentDate = liability.getStartDate().plusMonths(1);

        for (int month = 1; month <= totalMonths; month++) {
            double interestComponent = outstanding * monthlyRate;
            double principalComponent = liability.getMonthlyEmi().doubleValue() - interestComponent;
            outstanding -= principalComponent;

            if (outstanding < 0) outstanding = 0;

            schedule.add(EMIScheduleEntry.builder()
                    .emiNumber(month)
                    .dueDate(currentDate)
                    .emiAmount(liability.getMonthlyEmi())
                    .principalComponent(BigDecimal.valueOf(principalComponent).setScale(2, RoundingMode.HALF_UP))
                    .interestComponent(BigDecimal.valueOf(interestComponent).setScale(2, RoundingMode.HALF_UP))
                    .outstandingBalance(BigDecimal.valueOf(outstanding).setScale(2, RoundingMode.HALF_UP))
                    .isPaid(false)
                    .build());

            currentDate = currentDate.plusMonths(1);
        }

        return schedule;
    }

    @Transactional
    public Liability markEMIPaid(UUID liabilityId, LocalDate paymentDate, BigDecimal amount) {
        Liability liability = liabilityRepository.findById(liabilityId)
                .orElseThrow(() -> new IllegalArgumentException("Liability not found"));

        BigDecimal deductAmount = amount != null ? amount : liability.getMonthlyEmi();
        BigDecimal newOutstanding = liability.getOutstandingAmount().subtract(deductAmount);
        liability.setOutstandingAmount(newOutstanding.max(BigDecimal.ZERO));
        liability.setNextEmiDate(liability.getNextEmiDate().plusMonths(1));
        liability.setUpdatedAt(java.time.LocalDateTime.now());

        return liabilityRepository.save(liability);
    }

    @Transactional
    public void deleteLiability(UUID userId, UUID liabilityId) {
        Liability liability = liabilityRepository.findById(liabilityId)
                .orElseThrow(() -> new IllegalArgumentException("Liability not found"));
        if (!liability.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to delete this liability");
        }
        liabilityRepository.deleteById(liabilityId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLoanSummary(UUID liabilityId) {
        Liability liability = liabilityRepository.findById(liabilityId)
                .orElseThrow(() -> new IllegalArgumentException("Liability not found"));

        long totalMonths = java.time.temporal.ChronoUnit.MONTHS.between(liability.getStartDate(), liability.getEndDate());
        long elapsedMonths = java.time.temporal.ChronoUnit.MONTHS.between(liability.getStartDate(), LocalDate.now());
        long remainingMonths = totalMonths - elapsedMonths;

        BigDecimal totalPayable = liability.getMonthlyEmi().multiply(BigDecimal.valueOf(totalMonths));
        BigDecimal totalInterest = totalPayable.subtract(liability.getOriginalAmount());
        BigDecimal paidAmount = liability.getOriginalAmount().subtract(liability.getOutstandingAmount());
        BigDecimal paidInterest = totalInterest.multiply(
                BigDecimal.valueOf(elapsedMonths).divide(BigDecimal.valueOf(totalMonths), 4, RoundingMode.HALF_UP));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("originalAmount", liability.getOriginalAmount());
        summary.put("outstandingAmount", liability.getOutstandingAmount());
        summary.put("monthlyEmi", liability.getMonthlyEmi());
        summary.put("interestRate", liability.getInterestRate());
        summary.put("totalPayable", totalPayable);
        summary.put("totalInterest", totalInterest);
        summary.put("paidAmount", paidAmount);
        summary.put("paidInterest", paidInterest);
        summary.put("progressPercentage", paidAmount.divide(liability.getOriginalAmount(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
        summary.put("remainingMonths", remainingMonths);
        summary.put("nextEmiDate", liability.getNextEmiDate());
        return summary;
    }

    public BigDecimal calculateEMI(BigDecimal principal, BigDecimal annualRate, long tenureMonths) {
        double monthlyRate = annualRate.doubleValue() / 12 / 100;
        double emi = principal.doubleValue() * monthlyRate *
                Math.pow(1 + monthlyRate, tenureMonths) /
                (Math.pow(1 + monthlyRate, tenureMonths) - 1);

        return BigDecimal.valueOf(emi).setScale(2, RoundingMode.HALF_UP);
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
