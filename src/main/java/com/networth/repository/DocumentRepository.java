package com.networth.repository;

import com.networth.model.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Document> findByDematAccountIdOrderByCreatedAtDesc(UUID dematAccountId);
    List<Document> findByHoldingIdOrderByCreatedAtDesc(UUID holdingId);
    List<Document> findBySalaryIdOrderByCreatedAtDesc(UUID salaryId);
    Optional<Document> findTopBySalaryId(UUID salaryId);
    long countByDematAccountId(UUID dematAccountId);
    long countByHoldingId(UUID holdingId);
    long countBySalaryId(UUID salaryId);
}
