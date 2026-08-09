package com.networth.model.entity;

import com.networth.model.enums.FamilyRole;
import com.networth.model.enums.MembershipStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "family_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FamilyRole role = FamilyRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.PENDING;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @CreationTimestamp
    @Column(name = "invited_at", updatable = false)
    private Instant invitedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;
}
