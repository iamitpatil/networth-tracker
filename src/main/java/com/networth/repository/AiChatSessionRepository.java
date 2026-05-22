package com.networth.repository;

import com.networth.model.entity.AiChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiChatSessionRepository extends JpaRepository<AiChatSession, UUID> {
    List<AiChatSession> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    void deleteByUserIdAndId(UUID userId, UUID id);
}
