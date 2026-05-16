package com.networth.repository;

import com.networth.model.enums.MembershipStatus;
import com.networth.model.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, UUID> {
    List<FamilyMember> findByUserId(UUID userId);
    List<FamilyMember> findByFamilyId(UUID familyId);
    List<FamilyMember> findByUserIdAndStatus(UUID userId, MembershipStatus status);
    Optional<FamilyMember> findByFamilyIdAndUserId(UUID familyId, UUID userId);
    boolean existsByFamilyIdAndUserId(UUID familyId, UUID userId);
}
