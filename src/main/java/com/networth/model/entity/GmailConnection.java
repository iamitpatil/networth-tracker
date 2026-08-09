package com.networth.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gmail_connections")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GmailConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "gmail_address", nullable = false)
    private String gmailAddress;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expiry")
    private Instant tokenExpiry;

    @Column(name = "sync_enabled")
    @Builder.Default
    private Boolean syncEnabled = true;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
