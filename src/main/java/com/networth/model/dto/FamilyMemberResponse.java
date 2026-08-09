package com.networth.model.dto;

import com.networth.model.enums.FamilyRole;
import com.networth.model.enums.MembershipStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FamilyMemberResponse {
    private UUID id;
    private UUID familyId;
    private UUID userId;
    private String userName;
    private String userEmail;
    private FamilyRole role;
    private MembershipStatus status;
    private Instant invitedAt;
    private Instant respondedAt;
}
