package com.networth.repository;

import com.networth.model.entity.ItrFiling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItrFilingRepository extends JpaRepository<ItrFiling, UUID> {

    Optional<ItrFiling> findByUserIdAndFinancialYearAndFilingType(
            UUID userId, String financialYear, String filingType);

    List<ItrFiling> findByUserIdOrderByFinancialYearDescFilingDateDesc(UUID userId);

    List<ItrFiling> findByUserIdAndFinancialYear(UUID userId, String financialYear);
}
