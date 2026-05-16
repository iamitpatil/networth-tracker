package com.networth.repository;

import com.networth.model.entity.DematAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DematAccountRepository extends JpaRepository<DematAccount, UUID> {
    List<DematAccount> findByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByUserId(UUID userId);
}
