package com.networth.repository;

import com.networth.model.entity.AiPendingAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiPendingActionRepository extends JpaRepository<AiPendingAction, UUID> {
    List<AiPendingAction> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);
    Optional<AiPendingAction> findByIdAndUserId(UUID id, UUID userId);
    List<AiPendingAction> findBySessionIdAndStatus(UUID sessionId, String status);
    void deleteBySessionId(UUID sessionId);
}
