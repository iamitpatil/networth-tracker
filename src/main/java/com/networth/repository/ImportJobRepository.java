package com.networth.repository;

import com.networth.model.entity.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    List<ImportJob> findByUserId(UUID userId);
    List<ImportJob> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
