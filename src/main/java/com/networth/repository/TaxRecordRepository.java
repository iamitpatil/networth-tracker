package com.networth.repository;

import com.networth.model.entity.TaxRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaxRecordRepository extends JpaRepository<TaxRecord, UUID> {
    List<TaxRecord> findByUserId(UUID userId);
    List<TaxRecord> findByUserIdAndFinancialYear(UUID userId, String financialYear);
    List<TaxRecord> findByHoldingId(UUID holdingId);
}
