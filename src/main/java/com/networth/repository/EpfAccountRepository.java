package com.networth.repository;

import com.networth.model.entity.EpfAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EpfAccountRepository extends JpaRepository<EpfAccount, UUID> {
    List<EpfAccount> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
