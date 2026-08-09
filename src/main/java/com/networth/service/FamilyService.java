package com.networth.service;

import com.networth.model.dto.FamilyMemberResponse;
import com.networth.model.dto.FamilyResponse;
import com.networth.model.dto.InviteRequest;
import com.networth.model.entity.Family;
import com.networth.model.entity.FamilyMember;
import com.networth.model.entity.User;
import com.networth.model.enums.FamilyRole;
import com.networth.model.enums.MembershipStatus;
import com.networth.repository.FamilyMemberRepository;
import com.networth.repository.FamilyRepository;
import com.networth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public FamilyResponse createFamily(String name, UUID createdBy) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Family name is required");

        Family family = Family.builder()
                .name(name.trim())
                .createdBy(createdBy)
                .build();
        family = familyRepository.save(family);

        FamilyMember owner = FamilyMember.builder()
                .familyId(family.getId())
                .userId(createdBy)
                .role(FamilyRole.OWNER)
                .status(MembershipStatus.APPROVED)
                .invitedBy(createdBy)
                .respondedAt(Instant.now())
                .build();
        familyMemberRepository.save(owner);

        return toFamilyResponse(family, createdBy);
    }

    @Transactional
    public FamilyMemberResponse inviteMember(UUID familyId, UUID inviterId, InviteRequest request) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new IllegalArgumentException("Family not found"));

        FamilyMember inviter = familyMemberRepository.findByFamilyIdAndUserId(familyId, inviterId)
                .orElseThrow(() -> new IllegalArgumentException("You are not a member of this family"));

        if (inviter.getRole() == FamilyRole.MEMBER) {
            throw new IllegalArgumentException("Only owners and admins can invite members");
        }

        User invitedUser = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + request.getEmail()));

        if (familyMemberRepository.existsByFamilyIdAndUserId(familyId, invitedUser.getId())) {
            throw new IllegalArgumentException("User is already a member of this family");
        }

        FamilyMember member = FamilyMember.builder()
                .familyId(familyId)
                .userId(invitedUser.getId())
                .role(FamilyRole.MEMBER)
                .status(MembershipStatus.PENDING)
                .invitedBy(inviterId)
                .build();
        member = familyMemberRepository.save(member);

        try {
            notificationService.sendEmail(
                    invitedUser.getEmail(),
                    "Family Invitation: " + family.getName(),
                    "You have been invited to join the family \"" + family.getName() + "\" on NW Tracker.\n\n" +
                    "Log in to accept or decline this invitation."
            );
        } catch (Exception e) {
            log.warn("Failed to send invitation email to {}: {}", invitedUser.getEmail(), e.getMessage());
        }

        return toMemberResponse(member, invitedUser);
    }

    @Transactional
    public FamilyMemberResponse respondToInvitation(UUID membershipId, UUID userId, boolean accept) {
        FamilyMember member = familyMemberRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (!member.getUserId().equals(userId)) {
            throw new IllegalArgumentException("This invitation is not for you");
        }
        if (member.getStatus() != MembershipStatus.PENDING) {
            throw new IllegalArgumentException("Invitation already responded to");
        }

        member.setStatus(accept ? MembershipStatus.APPROVED : MembershipStatus.REJECTED);
        member.setRespondedAt(Instant.now());
        member = familyMemberRepository.save(member);

        User user = userRepository.findById(userId).orElse(null);
        return toMemberResponse(member, user);
    }

    @Transactional(readOnly = true)
    public List<FamilyResponse> getMyFamilies(UUID userId) {
        List<FamilyMember> memberships = familyMemberRepository.findByUserId(userId);
        return memberships.stream()
                .map(m -> familyRepository.findById(m.getFamilyId()).orElse(null))
                .filter(f -> f != null)
                .map(f -> toFamilyResponse(f, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FamilyMemberResponse> getFamilyMembers(UUID familyId, UUID userId) {
        FamilyMember requester = familyMemberRepository.findByFamilyIdAndUserId(familyId, userId)
                .orElseThrow(() -> new IllegalArgumentException("You are not a member of this family"));

        List<FamilyMember> members = familyMemberRepository.findByFamilyId(familyId);
        return members.stream()
                .map(m -> {
                    User u = userRepository.findById(m.getUserId()).orElse(null);
                    return toMemberResponse(m, u);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FamilyMemberResponse> getPendingInvitations(UUID userId) {
        List<FamilyMember> pending = familyMemberRepository.findByUserIdAndStatus(userId, MembershipStatus.PENDING);
        return pending.stream()
                .map(m -> {
                    User u = userRepository.findById(m.getUserId()).orElse(null);
                    return toMemberResponse(m, u);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> getApprovedMemberIds(UUID userId) {
        List<FamilyMember> memberships = familyMemberRepository.findByUserIdAndStatus(userId, MembershipStatus.APPROVED);
        return memberships.stream()
                .flatMap(m -> familyMemberRepository.findByFamilyId(m.getFamilyId()).stream())
                .filter(fm -> fm.getStatus() == MembershipStatus.APPROVED)
                .map(FamilyMember::getUserId)
                .distinct()
                .toList();
    }

    @Transactional
    public void leaveFamily(UUID familyId, UUID userId) {
        FamilyMember member = familyMemberRepository.findByFamilyIdAndUserId(familyId, userId)
                .orElseThrow(() -> new IllegalArgumentException("You are not a member of this family"));

        if (member.getRole() == FamilyRole.OWNER) {
            long remaining = familyMemberRepository.findByFamilyId(familyId).stream()
                    .filter(m -> m.getStatus() == MembershipStatus.APPROVED).count();
            if (remaining > 1) {
                throw new IllegalArgumentException("Transfer ownership before leaving, or delete the family");
            }
        }

        familyMemberRepository.delete(member);

        long memberCount = familyMemberRepository.findByFamilyId(familyId).size();
        if (memberCount == 0) {
            familyRepository.deleteById(familyId);
        }
    }

    @Transactional
    public void deleteFamily(UUID familyId, UUID userId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new IllegalArgumentException("Family not found"));
        if (!family.getCreatedBy().equals(userId)) {
            throw new IllegalArgumentException("Only the family creator can delete the family");
        }
        familyMemberRepository.findByFamilyId(familyId).forEach(fm ->
                familyMemberRepository.delete(fm));
        familyRepository.delete(family);
    }

    private FamilyResponse toFamilyResponse(Family family, UUID requesterId) {
        List<FamilyMember> members = familyMemberRepository.findByFamilyId(family.getId());
        User creator = userRepository.findById(family.getCreatedBy()).orElse(null);
        return FamilyResponse.builder()
                .id(family.getId())
                .name(family.getName())
                .createdBy(family.getCreatedBy())
                .createdByName(creator != null ? creator.getName() : null)
                .memberCount(members.size())
                .approvedCount((int) members.stream().filter(m -> m.getStatus() == MembershipStatus.APPROVED).count())
                .createdAt(family.getCreatedAt())
                .build();
    }

    private FamilyMemberResponse toMemberResponse(FamilyMember member, User user) {
        return FamilyMemberResponse.builder()
                .id(member.getId())
                .familyId(member.getFamilyId())
                .userId(member.getUserId())
                .userName(user != null ? user.getName() : null)
                .userEmail(user != null ? user.getEmail() : null)
                .role(member.getRole())
                .status(member.getStatus())
                .invitedAt(member.getInvitedAt())
                .respondedAt(member.getRespondedAt())
                .build();
    }
}
