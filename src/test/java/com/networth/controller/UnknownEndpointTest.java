package com.networth.controller;

import com.networth.repository.HoldingRepository;
import com.networth.repository.SymbolRepository;
import com.networth.service.FamilyService;
import com.networth.service.market.AmfiHistoricalService;
import com.networth.service.market.BackfillJobService;
import com.networth.service.market.UpstoxHistoricalService;
import com.networth.service.market.provider.MarketDataResolver;
import com.networth.service.market.provider.ProviderRateLimiter;
import com.networth.service.market.provider.ProviderRateLimits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the API answers for a route that does not exist.
 *
 * <p>Written while confirming that a deleted endpoint was really gone, and it was not what it looked
 * like: every unmatched path answered {@code 500 An unexpected error occurred} and logged a stack
 * trace under a correlation ID, because Spring 6.1 raises {@code NoResourceFoundException} for these
 * and only {@code NoHandlerFoundException} was handled. A caller was being told to retry a URL that
 * can never work, and client typos were arriving in the same log channel as real faults.
 *
 * <p>The body is asserted, not just the status, so this cannot pass on MockMvc's own default 404 --
 * the point is that the application's own handler produced it.
 */
@WebMvcTest(MarketDataController.class)
@AutoConfigureMockMvc(addFilters = false)
class UnknownEndpointTest {

    @Autowired MockMvc mockMvc;

    @MockBean UpstoxHistoricalService historicalService;
    @MockBean AmfiHistoricalService amfiHistoricalService;
    @MockBean BackfillJobService backfillJobService;
    @MockBean HoldingRepository holdingRepository;
    @MockBean SymbolRepository symbolRepository;
    @MockBean FamilyService familyService;
    @MockBean ProviderRateLimiter rateLimiter;
    @MockBean ProviderRateLimits rateLimits;
    @MockBean MarketDataResolver resolver;

    // The security beans the slice would otherwise try to build for real. addFilters = false keeps the
    // chain out of the request, but the filter is still a bean and still wants its collaborators.
    @MockBean com.networth.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean com.networth.security.JwtService jwtService;
    @MockBean org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @Test
    @DisplayName("an unmapped path is 404, not a server error")
    void unmappedPathIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/market/definitely-not-a-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Endpoint not found"));
    }

    @Test
    @DisplayName("the deleted backfill-prices endpoint is gone")
    void theOrphanedBackfillEndpointIsGone() throws Exception {
        // POST /market/backfill-prices was an unauthenticated duplicate of POST /market/backfill that
        // no caller used. Asserting the 404 keeps it from being reintroduced by a merge.
        mockMvc.perform(post("/api/v1/market/backfill-prices"))
                .andExpect(status().isNotFound());
    }
}
