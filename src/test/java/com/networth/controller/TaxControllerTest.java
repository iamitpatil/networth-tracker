package com.networth.controller;

import com.networth.repository.UserRepository;
import com.networth.service.DocumentService;
import com.networth.service.tax.*;
import com.networth.service.tax.rules.TaxRuleRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for the tax endpoints.
 *
 * <p>These exist because the two most recent defects in this area were contract bugs that no
 * unit test could catch: the harvesting response used different field names from the ones the
 * UI read, so every card rendered zero; and the regime comparison never sent the selected
 * financial year, so a past year was silently computed with the current year's rates. Both
 * live at the controller boundary.
 *
 * <p>A real {@link TaxRuleRegistry} is imported rather than mocked, so the year-resolution
 * behaviour these endpoints depend on is exercised for real.
 */
@WebMvcTest(TaxController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TaxRuleRegistry.class)
class TaxControllerTest {

    private static final String USER_ID = "11111111-2222-3333-4444-555555555555";

    @Autowired MockMvc mvc;

    @MockBean CapitalGainsCalculator capitalGainsCalculator;
    @MockBean TaxHarvestService taxHarvestService;
    @MockBean DeductionService deductionService;
    @MockBean TaxRegimeCalculator taxRegimeCalculator;
    @MockBean Form16Service form16Service;
    @MockBean ItrFilingService itrFilingService;
    @MockBean UserRepository userRepository;
    @MockBean DocumentService documentService;
    // SecurityConfig is picked up by @WebMvcTest, and it needs the JWT filter chain. Mocked
    // rather than excluded so the real filter chain wiring is still exercised.
    @MockBean com.networth.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean com.networth.security.JwtService jwtService;
    @MockBean org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    // ── supported years drive the UI selector ─────────────────────────

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("GET /financial-years exposes the years, the current one, and the caveats")
    void financialYearsEndpoint() throws Exception {
        mvc.perform(get("/api/v1/tax/financial-years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYears").isArray())
                .andExpect(jsonPath("$.financialYears[0]").value("2026-2027"))
                .andExpect(jsonPath("$.financialYears.length()").value(27))
                .andExpect(jsonPath("$.current").exists())
                // The UI needs these to caveat unverified years and hide the regime comparison
                // where only one regime existed.
                .andExpect(jsonPath("$.unverified").isArray())
                .andExpect(jsonPath("$.comparable").isArray());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("GET /rules/{fy} returns the year's own figures, not a hardcoded set")
    void rulesEndpointReflectsTheYear() throws Exception {
        // FY 2025-26: equity LTCG 12.5% over 1.25L.
        mvc.perform(get("/api/v1/tax/rules/2025-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capitalGains.equityLtcgRate").value(0.125))
                .andExpect(jsonPath("$.capitalGains.equityLtcgExemption").value(125000))
                .andExpect(jsonPath("$.regimes.NEW.standardDeduction").value(75000))
                .andExpect(jsonPath("$.regimes.OLD.standardDeduction").value(50000));

        // FY 2010-11: equity LTCG exempt under s.10(38), and no new regime existed.
        mvc.perform(get("/api/v1/tax/rules/2010-2011"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capitalGains.equityLtcgRate").value(0))
                .andExpect(jsonPath("$.regimes.NEW").doesNotExist())
                .andExpect(jsonPath("$.regimes.OLD").exists());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("an unsupported year is a 400, not a 500 or a silently wrong figure")
    void unsupportedYearIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/tax/rules/1999-2000"))
                .andExpect(status().isBadRequest());
    }

    // ── the financial year must reach the service ─────────────────────

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("harvesting passes the requested year through, and defaults to the current one")
    void harvestingPassesFinancialYear() throws Exception {
        when(taxHarvestService.findHarvestingOpportunities(any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/tax/harvesting-opportunities").param("financialYear", "2021-2022"))
                .andExpect(status().isOk());
        verify(taxHarvestService).findHarvestingOpportunities(UUID.fromString(USER_ID), "2021-2022");

        // Omitted, it must resolve to the current year rather than a pinned literal. Two
        // endpoints previously defaulted to "2024-2025", which quietly became a past year.
        mvc.perform(get("/api/v1/tax/harvesting-opportunities"))
                .andExpect(status().isOk());
        verify(taxHarvestService, never())
                .findHarvestingOpportunities(UUID.fromString(USER_ID), "2024-2025");
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("regime comparison forwards the financial year from the request body")
    void compareForwardsFinancialYear() throws Exception {
        when(taxRegimeCalculator.compareRegimes(any(), any(), any(), any(), any(), any()))
                .thenReturn(TaxRegimeCalculator.RegimeComparison.builder()
                        .oldRegime(TaxRegimeCalculator.TaxComputation.zero(com.networth.model.enums.TaxRegime.OLD))
                        .newRegime(TaxRegimeCalculator.TaxComputation.zero(com.networth.model.enums.TaxRegime.NEW))
                        .recommended(com.networth.model.enums.TaxRegime.NEW)
                        .savings(BigDecimal.ZERO)
                        .build());

        mvc.perform(post("/api/v1/tax/regime/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossSalary\":1500000,\"financialYear\":\"2023-2024\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").value("2023-2024"));

        verify(taxRegimeCalculator).compareRegimes(any(), any(), any(), any(), any(), eq("2023-2024"));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("comparison without a gross salary is rejected")
    void compareRequiresGrossSalary() throws Exception {
        mvc.perform(post("/api/v1/tax/regime/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"financialYear\":\"2025-2026\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("tax calculation forwards the year and echoes it back")
    void calculateForwardsFinancialYear() throws Exception {
        when(taxRegimeCalculator.calculateTax(any(), any(), any()))
                .thenReturn(TaxRegimeCalculator.TaxComputation.zero(com.networth.model.enums.TaxRegime.NEW));

        mvc.perform(post("/api/v1/tax/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxableIncome\":1200000,\"regime\":\"NEW\",\"financialYear\":\"2025-26\"}"))
                .andExpect(status().isOk())
                // Canonicalised on the way out, so the client sees an unambiguous year.
                .andExpect(jsonPath("$.financialYear").value("2025-2026"));

        verify(taxRegimeCalculator).calculateTax(any(), eq(com.networth.model.enums.TaxRegime.NEW), eq("2025-26"));
    }

    // ── the response shape the UI reads ───────────────────────────────

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("capital gains response carries the equity fields the Tax page renders")
    void capitalGainsResponseShape() throws Exception {
        when(capitalGainsCalculator.calculateCapitalGains(any(), any())).thenReturn(Map.of(
                "financialYear", "2025-2026",
                "equity", Map.of(
                        "ltcg", new BigDecimal("1000"), "stcg", new BigDecimal("2000"),
                        "ltcl", BigDecimal.ZERO, "stcl", BigDecimal.ZERO,
                        "taxOnLTCG", BigDecimal.ZERO, "taxOnSTCG", new BigDecimal("400")),
                "totalTax", new BigDecimal("400"),
                "cess", new BigDecimal("16")));

        mvc.perform(get("/api/v1/tax/summary/2025-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equity.ltcg").exists())
                .andExpect(jsonPath("$.equity.stcg").exists())
                .andExpect(jsonPath("$.equity.taxOnLTCG").exists())
                .andExpect(jsonPath("$.equity.taxOnSTCG").exists())
                .andExpect(jsonPath("$.totalTax").exists());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("harvesting response carries the exact field names the UI reads")
    void harvestingResponseShape() throws Exception {
        // The UI reads currentLoss, potentialSavings and quantity. When the backend returned
        // unrealizedGain/taxSavings and no quantity, every card silently rendered zero.
        when(taxHarvestService.findHarvestingOpportunities(any(), any())).thenReturn(List.of(Map.of(
                "holdingId", UUID.randomUUID(), "symbol", "TESTCO",
                "quantity", new BigDecimal("100"), "currentLoss", new BigDecimal("4000"),
                "unrealizedGain", new BigDecimal("-4000"), "potentialSavings", new BigDecimal("800"),
                "type", "LOSS_HARVEST", "action", "sell_to_book_loss",
                "holdingDays", 60L, "reason", "because")));

        mvc.perform(get("/api/v1/tax/harvesting-opportunities").param("financialYear", "2025-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("TESTCO"))
                .andExpect(jsonPath("$[0].quantity").exists())
                .andExpect(jsonPath("$[0].currentLoss").exists())
                .andExpect(jsonPath("$[0].potentialSavings").exists())
                .andExpect(jsonPath("$[0].type").value("LOSS_HARVEST"))
                .andExpect(jsonPath("$[0].reason").exists());
    }
}
