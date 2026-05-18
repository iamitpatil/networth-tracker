package com.networth.repository;

import com.networth.model.entity.Form16Data;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface Form16Repository extends JpaRepository<Form16Data, UUID> {

    Optional<Form16Data> findByUserIdAndFinancialYear(UUID userId, String financialYear);

    List<Form16Data> findByUserIdOrderByFinancialYearDesc(UUID userId);

    boolean existsByUserIdAndFinancialYear(UUID userId, String financialYear);
}
