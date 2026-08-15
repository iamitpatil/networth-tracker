package com.networth.controller;

import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access control on the per-holding price-history endpoint.
 *
 * <p>The series it returns is public market data, which is why it originally had no check at all.
 * The holding is not public though: answering for any UUID confirmed whether that holding existed
 * and what asset class it was. Family members must still get through, because the family view
 * renders other members' holdings and opens their charts.
 */
@WebMvcTest(PortfolioController.class)
@AutoConfigureMockMvc(addFilters = false)
class PortfolioPriceHistoryAccessTest {

    private static final String OWNER = "11111111-1111-1111-1111-111111111111";
    private static final String FAMILY_MEMBER = "22222222-2222-2222-2222-222222222222";
    private static final String STRANGER = "33333333-3333-3333-3333-333333333333";

    private final UUID holdingId = UUID.randomUUID();

    @Autowired MockMvc mvc;

    @MockBean com.networth.service.portfolio.HoldingService holdingService;
    @MockBean com.networth.service.portfolio.TransactionService transactionService;
    @MockBean com.networth.service.portfolio.CorporateActionService corporateActionService;
    @MockBean com.networth.service.portfolio.CorporateActionReclassifier corporateActionReclassifier;
    @MockBean com.networth.service.importservice.TransactionImportService transactionImportService;
    @MockBean com.networth.service.portfolio.PortfolioSummaryService portfolioSummaryService;
    @MockBean com.networth.service.FamilyDataService familyDataService;
    @MockBean com.networth.service.FamilyService familyService;
    @MockBean com.networth.service.InvestmentOverTimeService investmentOverTimeService;
    @MockBean com.networth.repository.HoldingRepository holdingRepository;
    @MockBean com.networth.repository.StockPriceHistoryRepository stockPriceHistoryRepository;
    @MockBean com.networth.repository.SymbolRepository symbolRepository;
    @MockBean com.networth.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean com.networth.security.JwtService jwtService;
    @MockBean org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        Holding holding = new Holding();
        holding.setId(holdingId);
        holding.setUserId(UUID.fromString(OWNER));
        holding.setAssetType(AssetType.EQUITY);
        holding.setSymbol("RELIANCE");

        when(holdingRepository.findById(holdingId)).thenReturn(Optional.of(holding));
        when(holdingService.getEffectiveSymbolForPricing(any())).thenReturn("RELIANCE");
        when(stockPriceHistoryRepository
                .findBySymbolAndPriceDateBetweenOrderByPriceDate(any(), any(), any()))
                .thenReturn(List.of());

        // The owner shares a family with FAMILY_MEMBER; STRANGER shares none.
        when(familyService.getApprovedMemberIds(UUID.fromString(OWNER)))
                .thenReturn(List.of(UUID.fromString(OWNER), UUID.fromString(FAMILY_MEMBER)));
        when(familyService.getApprovedMemberIds(UUID.fromString(FAMILY_MEMBER)))
                .thenReturn(List.of(UUID.fromString(OWNER), UUID.fromString(FAMILY_MEMBER)));
        when(familyService.getApprovedMemberIds(UUID.fromString(STRANGER)))
                .thenReturn(List.of(UUID.fromString(STRANGER)));
    }

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("the owner can read their holding's price history")
    void ownerIsAllowed() throws Exception {
        mvc.perform(get("/api/v1/portfolio/holdings/" + holdingId + "/price-history"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = FAMILY_MEMBER)
    @DisplayName("an approved family member can too, so the family view keeps working")
    void familyMemberIsAllowed() throws Exception {
        mvc.perform(get("/api/v1/portfolio/holdings/" + holdingId + "/price-history"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = STRANGER)
    @DisplayName("an unrelated user gets the same answer as for a holding that does not exist")
    void strangerCannotProbe() throws Exception {
        // 404 rather than 403: a distinct forbidden status would still tell the caller the
        // holding is real, which is the disclosure this check exists to prevent.
        mvc.perform(get("/api/v1/portfolio/holdings/" + holdingId + "/price-history"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = STRANGER)
    @DisplayName("a holding that genuinely does not exist is also a 404")
    void missingHoldingIsIndistinguishable() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(holdingRepository.findById(unknown)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/portfolio/holdings/" + unknown + "/price-history"))
                .andExpect(status().isNotFound());
    }
}
