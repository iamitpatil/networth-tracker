package com.networth.service.tax;

import com.networth.exception.AccessDeniedException;
import com.networth.exception.ResourceNotFoundException;
import com.networth.model.entity.ItrFiling;
import com.networth.repository.ItrFilingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItrFilingService {

    private final ItrFilingRepository itrFilingRepository;

    @Transactional(readOnly = true)
    public List<ItrFiling> getUserItrFilings(UUID userId) {
        return itrFilingRepository.findByUserIdOrderByFinancialYearDescFilingDateDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<ItrFiling> getByYear(UUID userId, String financialYear) {
        return itrFilingRepository.findByUserIdAndFinancialYear(userId, financialYear);
    }

    @Transactional
    public ItrFiling save(UUID userId, ItrFiling filing) {
        if (filing.getFinancialYear() == null) {
            throw new IllegalArgumentException("Financial year is required");
        }
        filing.setUserId(userId);
        if (filing.getFilingType() == null) {
            filing.setFilingType("ORIGINAL");
        }
        return itrFilingRepository.save(filing);
    }

    @Transactional
    public ItrFiling update(UUID userId, UUID itrId, ItrFiling updates) {
        ItrFiling existing = findOwned(userId, itrId);
        // Merge non-null fields
        if (updates.getItrFormType() != null) existing.setItrFormType(updates.getItrFormType());
        if (updates.getAcknowledgementNumber() != null) existing.setAcknowledgementNumber(updates.getAcknowledgementNumber());
        if (updates.getFilingDate() != null) existing.setFilingDate(updates.getFilingDate());
        if (updates.getFilingType() != null) existing.setFilingType(updates.getFilingType());
        if (updates.getEVerified() != null) existing.setEVerified(updates.getEVerified());
        if (updates.getEVerificationMode() != null) existing.setEVerificationMode(updates.getEVerificationMode());
        if (updates.getGrossTotalIncome() != null) existing.setGrossTotalIncome(updates.getGrossTotalIncome());
        if (updates.getTotalDeductions() != null) existing.setTotalDeductions(updates.getTotalDeductions());
        if (updates.getTotalTaxableIncome() != null) existing.setTotalTaxableIncome(updates.getTotalTaxableIncome());
        if (updates.getTotalTaxPayable() != null) existing.setTotalTaxPayable(updates.getTotalTaxPayable());
        if (updates.getTdsTotal() != null) existing.setTdsTotal(updates.getTdsTotal());
        if (updates.getAdvanceTaxPaid() != null) existing.setAdvanceTaxPaid(updates.getAdvanceTaxPaid());
        if (updates.getSelfAssessmentTax() != null) existing.setSelfAssessmentTax(updates.getSelfAssessmentTax());
        if (updates.getTaxRefund() != null) existing.setTaxRefund(updates.getTaxRefund());
        if (updates.getRefundStatus() != null) existing.setRefundStatus(updates.getRefundStatus());
        if (updates.getTaxRegime() != null) existing.setTaxRegime(updates.getTaxRegime());
        if (updates.getDocumentId() != null) existing.setDocumentId(updates.getDocumentId());
        return itrFilingRepository.save(existing);
    }

    @Transactional
    public void delete(UUID userId, UUID itrId) {
        ItrFiling filing = findOwned(userId, itrId);
        itrFilingRepository.delete(filing);
    }

    private ItrFiling findOwned(UUID userId, UUID itrId) {
        ItrFiling filing = itrFilingRepository.findById(itrId)
                .orElseThrow(() -> new ResourceNotFoundException("ITR Filing", itrId.toString()));
        if (!filing.getUserId().equals(userId)) {
            throw new AccessDeniedException("ITR Filing", itrId.toString());
        }
        return filing;
    }
}
