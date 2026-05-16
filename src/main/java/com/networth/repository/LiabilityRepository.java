package com.networth.repository;

import com.networth.model.entity.Liability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LiabilityRepository extends JpaRepository<Liability, UUID> {
    List<Liability> findByUserId(UUID userId);
}
