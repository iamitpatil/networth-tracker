package com.networth;

import com.networth.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class NetWorthTrackerApplicationTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void contextLoads() {
    }

    @Test
    void jwtTokenGeneration() {
        String token = jwtService.generateAccessToken("test-user", "test@example.com");
        assertNotNull(token);
        assertEquals("test-user", jwtService.extractUserId(token));
        assertEquals("test@example.com", jwtService.extractEmail(token));
        assertTrue(jwtService.isTokenValid(token));
    }
}
