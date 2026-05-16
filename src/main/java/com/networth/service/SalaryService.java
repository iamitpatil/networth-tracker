package com.networth.service;

import com.networth.model.entity.Salary;
import com.networth.repository.SalaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalaryService {

    private final SalaryRepository repository;

    @Transactional(readOnly = true)
    public List<Salary> getUserSalaries(UUID userId) {
        return repository.findByUserIdOrderByPayDateDesc(userId);
    }

    @Transactional
    public Salary createSalary(UUID userId, SalaryRequest req) {
        Salary salary = Salary.builder()
                .userId(userId)
                .employerName(req.employerName())
                .amount(req.amount())
                .bankAccountId(req.bankAccountId())
                .payDate(req.payDate() != null ? req.payDate() : LocalDate.now())
                .notes(req.notes())
                .components(req.components())
                .build();
        return repository.save(salary);
    }

    @Transactional
    public Salary updateSalary(UUID userId, UUID salaryId, SalaryRequest req) {
        Salary salary = repository.findById(salaryId)
                .orElseThrow(() -> new IllegalArgumentException("Salary record not found"));
        if (!salary.getUserId().equals(userId))
            throw new IllegalArgumentException("Access denied");
        if (req.employerName() != null) salary.setEmployerName(req.employerName());
        if (req.amount() != null) salary.setAmount(req.amount());
        if (req.bankAccountId() != null) salary.setBankAccountId(req.bankAccountId());
        if (req.payDate() != null) salary.setPayDate(req.payDate());
        if (req.notes() != null) salary.setNotes(req.notes());
        return repository.save(salary);
    }

    @Transactional
    public void deleteSalary(UUID userId, UUID salaryId) {
        Salary salary = repository.findById(salaryId)
                .orElseThrow(() -> new IllegalArgumentException("Salary record not found"));
        if (!salary.getUserId().equals(userId))
            throw new IllegalArgumentException("Access denied");
        repository.delete(salary);
    }

    @Transactional(readOnly = true)
    public BigDecimal getMonthlyIncome(UUID userId) {
        return repository.findByUserIdOrderByPayDateDesc(userId).stream()
                .filter(s -> s.getPayDate() != null)
                .filter(s -> s.getPayDate().getMonthValue() == LocalDate.now().getMonthValue()
                        && s.getPayDate().getYear() == LocalDate.now().getYear())
                .map(Salary::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public Salary getSalary(UUID salaryId) {
        return repository.findById(salaryId)
                .orElseThrow(() -> new IllegalArgumentException("Salary not found"));
    }

    public record SalaryRequest(
            String employerName, BigDecimal amount, UUID bankAccountId,
            LocalDate payDate, String notes, Map<String, Object> components,
            String documentId) {}
}
