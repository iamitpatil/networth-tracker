package com.networth.controller;

import com.networth.service.analytics.AnalyticsService;
import com.networth.service.SIPCalendarService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for the analytics endpoints.
 *
 * <p>The important one is XIRR: it must be able to say "unknown" without that being confused
 * with 0%. The old implementation could not — it always returned a number, including a seed
 * value of 10% on a portfolio that had lost everything.
 */
@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerTest {

    private static final String USER_ID = "11111111-2222-3333-4444-555555555555";

    @Autowired MockMvc mvc;

    @MockBean AnalyticsService analyticsService;
    @MockBean SIPCalendarService sipCalendarService;
    @MockBean com.networth.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean com.networth.security.JwtService jwtService;
    @MockBean org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("an unknown XIRR serialises as null with computed=false, not as zero")
    void unknownXirrIsNull() throws Exception {
        when(analyticsService.calculateXIRR(any())).thenReturn(null);

        mvc.perform(get("/api/v1/analytics/xirr"))
                .andExpect(status().isOk())
                // Map.of would have thrown on a null value; the response must tolerate it,
                // because "cannot be determined" is a real answer here.
                .andExpect(jsonPath("$.xirr").doesNotExist())
                .andExpect(jsonPath("$.computed").value(false));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("a computed XIRR is returned with computed=true")
    void computedXirrIsReported() throws Exception {
        when(analyticsService.calculateXIRR(any())).thenReturn(new BigDecimal("0.1234"));

        mvc.perform(get("/api/v1/analytics/xirr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xirr").value(0.1234))
                .andExpect(jsonPath("$.computed").value(true));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("CAGR endpoint responds")
    void cagrEndpoint() throws Exception {
        when(analyticsService.calculateCAGR(any())).thenReturn(new BigDecimal("0.08"));

        mvc.perform(get("/api/v1/analytics/cagr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cagr").value(0.08));
    }
}
