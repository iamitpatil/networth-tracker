package com.networth.repository;

import com.networth.model.entity.CcSpendReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CcSpendReportRepository extends JpaRepository<CcSpendReport, UUID> {
    List<CcSpendReport> findByUserIdOrderByStatementMonthDesc(UUID userId);
    List<CcSpendReport> findByUserIdAndCardLastFourOrderByStatementMonthDesc(UUID userId, String cardLastFour);
    Optional<CcSpendReport> findByUserIdAndCardLastFourAndStatementMonth(UUID userId, String cardLastFour, String statementMonth);

    @Query("SELECT DISTINCT r.cardLastFour, r.cardIssuer, r.cardType FROM CcSpendReport r WHERE r.userId = :userId")
    List<Object[]> findDistinctCardsByUserId(UUID userId);

    @Query("SELECT r.statementMonth, SUM(r.totalAmountDue) FROM CcSpendReport r WHERE r.userId = :userId GROUP BY r.statementMonth ORDER BY r.statementMonth DESC")
    List<Object[]> findMonthlyTotalsByUserId(UUID userId);
}
