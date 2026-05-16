package com.networth.repository;

import com.networth.model.entity.GmailConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GmailConnectionRepository extends JpaRepository<GmailConnection, UUID> {
    Optional<GmailConnection> findByUserId(UUID userId);
}
