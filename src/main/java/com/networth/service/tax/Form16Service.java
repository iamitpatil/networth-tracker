package com.networth.service.tax;

import com.networth.exception.AccessDeniedException;
import com.networth.exception.ResourceNotFoundException;
import com.networth.model.entity.Form16Data;
import com.networth.repository.Form16Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class Form16Service {

    private final Form16Repository form16Repository;
    private final Form16Parser form16Parser;

    @Transactional(readOnly = true)
    public List<Form16Data> getUserForm16s(UUID userId) {
        return form16Repository.findByUserIdOrderByFinancialYearDesc(userId);
    }

    @Transactional(readOnly = true)
    public Optional<Form16Data> getForUserAndYear(UUID userId, String financialYear) {
        return form16Repository.findByUserIdAndFinancialYear(userId, financialYear);
    }

    @Transactional
    public Form16Data parseAndSave(UUID userId, MultipartFile file, String financialYear) throws IOException {
        Form16Data parsed = form16Parser.parse(file);
        parsed.setUserId(userId);

        // Use provided FY if parser couldn't detect it
        if (parsed.getFinancialYear() == null && financialYear != null) {
            parsed.setFinancialYear(financialYear);
        }
        if (parsed.getFinancialYear() == null) {
            throw new IllegalArgumentException("Financial year could not be detected. Please specify it.");
        }

        // Update existing or create new
        Optional<Form16Data> existing = form16Repository.findByUserIdAndFinancialYear(
                userId, parsed.getFinancialYear());

        if (existing.isPresent()) {
            Form16Data existingForm = existing.get();
            // Merge - only update non-null fields from parsed
            mergeForm16(existingForm, parsed);
            log.info("Updated existing Form 16 for user {} FY {}", userId, parsed.getFinancialYear());
            return form16Repository.save(existingForm);
        } else {
            log.info("Created new Form 16 for user {} FY {}", userId, parsed.getFinancialYear());
            return form16Repository.save(parsed);
        }
    }

    @Transactional
    public Form16Data saveManual(UUID userId, Form16Data data) {
        if (data.getFinancialYear() == null) {
            throw new IllegalArgumentException("Financial year is required");
        }
        data.setUserId(userId);
        data.setSource("MANUAL");
        data.setParseConfidence(100);
        return form16Repository.save(data);
    }

    @Transactional
    public void delete(UUID userId, UUID form16Id) {
        Form16Data form16 = form16Repository.findById(form16Id)
                .orElseThrow(() -> new ResourceNotFoundException("Form16", form16Id.toString()));
        if (!form16.getUserId().equals(userId)) {
            throw new AccessDeniedException("Form16", form16Id.toString());
        }
        form16Repository.delete(form16);
    }

    private void mergeForm16(Form16Data existing, Form16Data parsed) {
        if (parsed.getEmployerName() != null) existing.setEmployerName(parsed.getEmployerName());
        if (parsed.getEmployerPan() != null) existing.setEmployerPan(parsed.getEmployerPan());
        if (parsed.getEmployerTan() != null) existing.setEmployerTan(parsed.getEmployerTan());
        if (parsed.getEmployeePan() != null) existing.setEmployeePan(parsed.getEmployeePan());

        if (isPositive(parsed.getGrossSalary())) existing.setGrossSalary(parsed.getGrossSalary());
        if (isPositive(parsed.getExemptAllowances())) existing.setExemptAllowances(parsed.getExemptAllowances());
        if (isPositive(parsed.getStandardDeduction())) existing.setStandardDeduction(parsed.getStandardDeduction());
        if (isPositive(parsed.getProfessionalTax())) existing.setProfessionalTax(parsed.getProfessionalTax());
        if (isPositive(parsed.getTaxableSalary())) existing.setTaxableSalary(parsed.getTaxableSalary());

        if (isPositive(parsed.getTdsTotal())) existing.setTdsTotal(parsed.getTdsTotal());
        if (parsed.getQuarterlyTds() != null) existing.setQuarterlyTds(parsed.getQuarterlyTds());

        if (isPositive(parsed.getSection80c())) existing.setSection80c(parsed.getSection80c());
        if (isPositive(parsed.getSection80ccd1b())) existing.setSection80ccd1b(parsed.getSection80ccd1b());
        if (isPositive(parsed.getSection80d())) existing.setSection80d(parsed.getSection80d());
        if (isPositive(parsed.getSection80g())) existing.setSection80g(parsed.getSection80g());

        if (parsed.getRawData() != null) existing.setRawData(parsed.getRawData());
        if (parsed.getParseConfidence() != null) existing.setParseConfidence(parsed.getParseConfidence());
        if (parsed.getDocumentId() != null) existing.setDocumentId(parsed.getDocumentId());
    }

    private boolean isPositive(java.math.BigDecimal value) {
        return value != null && value.compareTo(java.math.BigDecimal.ZERO) > 0;
    }
}
