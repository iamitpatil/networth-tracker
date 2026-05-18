package com.networth.service;

import com.networth.exception.AccessDeniedException;
import com.networth.exception.ResourceNotFoundException;
import com.networth.model.entity.Salary;
import com.networth.repository.SalaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
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
        Salary salary = findOwnedSalary(userId, salaryId);
        if (req.employerName() != null) salary.setEmployerName(req.employerName());
        if (req.amount() != null) salary.setAmount(req.amount());
        if (req.bankAccountId() != null) salary.setBankAccountId(req.bankAccountId());
        if (req.payDate() != null) salary.setPayDate(req.payDate());
        if (req.notes() != null) salary.setNotes(req.notes());
        return repository.save(salary);
    }

    @Transactional
    public void deleteSalary(UUID userId, UUID salaryId) {
        Salary salary = findOwnedSalary(userId, salaryId);
        repository.delete(salary);
    }

    /**
     * Find a salary ensuring it belongs to the given user.
     */
    private Salary findOwnedSalary(UUID userId, UUID salaryId) {
        Salary salary = repository.findById(salaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Salary", salaryId.toString()));
        if (!salary.getUserId().equals(userId)) {
            log.warn("User {} attempted to access salary {} owned by {}", userId, salaryId, salary.getUserId());
            throw new AccessDeniedException("Salary", salaryId.toString());
        }
        return salary;
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
    public Salary getSalary(UUID userId, UUID salaryId) {
        return findOwnedSalary(userId, salaryId);
    }

    public record SalaryRequest(
            String employerName, BigDecimal amount, UUID bankAccountId,
            LocalDate payDate, String notes, Map<String, Object> components,
            String documentId) {}
}
