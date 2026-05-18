package com.networth.service;

import com.networth.exception.AccessDeniedException;
import com.networth.exception.ResourceNotFoundException;
import com.networth.model.entity.Liability;
import com.networth.repository.LiabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EMIServiceTest {

    @Mock
    private LiabilityRepository liabilityRepository;

    @InjectMocks
    private EMIService emiService;

    private UUID userId;
    private UUID otherUserId;
    private UUID liabilityId;
    private Liability liability;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        liabilityId = UUID.randomUUID();

        liability = Liability.builder()
                .id(liabilityId)
                .userId(userId)
                .originalAmount(new BigDecimal("1000000"))
                .outstandingAmount(new BigDecimal("1000000"))
                .interestRate(new BigDecimal("8.5"))
                .monthlyEmi(new BigDecimal("9847.43"))
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2034, 1, 1))
                .nextEmiDate(LocalDate.of(2024, 2, 1))
                .build();
    }

    // ===== EMI Calculation Tests =====

    @Test
    void calculateEMI_withNormalRate_returnsCorrectValue() {
        // 10 lakh at 8.5% for 120 months
        BigDecimal emi = emiService.calculateEMI(
                new BigDecimal("1000000"),
                new BigDecimal("8.5"),
                120);

        // Expected ~12,398.57 (BigDecimal precision result)
        BigDecimal expected = new BigDecimal("12398.57");
        BigDecimal tolerance = new BigDecimal("1.00");
        assertTrue(emi.subtract(expected).abs().compareTo(tolerance) <= 0,
                "EMI " + emi + " should be within ₹1 of " + expected);
    }

    @Test
    void calculateEMI_withZeroInterest_returnsPrincipalDividedByMonths() {
        BigDecimal emi = emiService.calculateEMI(
                new BigDecimal("120000"),
                BigDecimal.ZERO,
                12);

        assertEquals(new BigDecimal("10000.00"), emi);
    }

    @Test
    void calculateEMI_withNegativePrincipal_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                emiService.calculateEMI(new BigDecimal("-1000"), new BigDecimal("8.5"), 120));
    }

    @Test
    void calculateEMI_withZeroTenure_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                emiService.calculateEMI(new BigDecimal("1000000"), new BigDecimal("8.5"), 0));
    }

    @Test
    void calculateEMI_withNegativeRate_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                emiService.calculateEMI(new BigDecimal("1000000"), new BigDecimal("-1"), 120));
    }

    // ===== IDOR / Ownership Tests =====

    @Test
    void deleteLiability_byOwner_succeeds() {
        when(liabilityRepository.findById(liabilityId)).thenReturn(Optional.of(liability));
        when(liabilityRepository.save(any())).thenReturn(liability);

        assertDoesNotThrow(() -> emiService.deleteLiability(userId, liabilityId));
    }

    @Test
    void deleteLiability_byNonOwner_throwsAccessDenied() {
        when(liabilityRepository.findById(liabilityId)).thenReturn(Optional.of(liability));

        assertThrows(AccessDeniedException.class, () ->
                emiService.deleteLiability(otherUserId, liabilityId));
    }

    @Test
    void deleteLiability_notFound_throwsResourceNotFound() {
        when(liabilityRepository.findById(liabilityId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                emiService.deleteLiability(userId, liabilityId));
    }

    @Test
    void generateEMISchedule_byNonOwner_throwsAccessDenied() {
        when(liabilityRepository.findById(liabilityId)).thenReturn(Optional.of(liability));

        assertThrows(AccessDeniedException.class, () ->
                emiService.generateEMISchedule(otherUserId, liabilityId));
    }

    @Test
    void getLoanSummary_byNonOwner_throwsAccessDenied() {
        when(liabilityRepository.findById(liabilityId)).thenReturn(Optional.of(liability));

        assertThrows(AccessDeniedException.class, () ->
                emiService.getLoanSummary(otherUserId, liabilityId));
    }

    @Test
    void markEMIPaid_byNonOwner_throwsAccessDenied() {
        when(liabilityRepository.findById(liabilityId)).thenReturn(Optional.of(liability));

        assertThrows(AccessDeniedException.class, () ->
                emiService.markEMIPaid(otherUserId, liabilityId, LocalDate.now(), null));
    }
}
