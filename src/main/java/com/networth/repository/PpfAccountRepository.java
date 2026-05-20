package com.networth.repository;

import com.networth.model.entity.PpfAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PpfAccountRepository extends JpaRepository<PpfAccount, UUID> {
    List<PpfAccount> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
