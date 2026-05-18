package com.networth.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private JwtService jwtService;
    private Environment env;

    @BeforeEach
    void setUp() {
        env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"test"});

        jwtService = new JwtService(env);
        // Set valid secret (base64 of "test-secret-key-must-be-at-least-32-bytes-long-for-hmac-sha384")
        ReflectionTestUtils.setField(jwtService, "secret",
                "dGVzdC1zZWNyZXQta2V5LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZy1mb3ItaG1hYy1zaGEzODQ=");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 900000L); // 15min
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 86400000L); // 24h
        ReflectionTestUtils.setField(jwtService, "issuer", "test-issuer");
    }

    @Test
    void generateAccessToken_includesTypeClaim() {
        String token = jwtService.generateAccessToken("user-123", "test@example.com");
        Claims claims = jwtService.parseToken(token);

        assertEquals("access", claims.get("type"));
        assertEquals("user-123", claims.getSubject());
        assertEquals("test@example.com", claims.get("email"));
        assertEquals("test-issuer", claims.getIssuer());
        assertNotNull(claims.getId(), "JTI should be set");
    }

    @Test
    void generateRefreshToken_includesTypeClaim() {
        String token = jwtService.generateRefreshToken("user-123", "test@example.com");
        Claims claims = jwtService.parseToken(token);

        assertEquals("refresh", claims.get("type"));
    }

    @Test
    void validateTokenOfType_acceptsMatchingType() {
        String accessToken = jwtService.generateAccessToken("user-123", "test@example.com");
        Claims claims = jwtService.validateTokenOfType(accessToken, JwtService.TOKEN_TYPE_ACCESS);

        assertNotNull(claims);
    }

    @Test
    void validateTokenOfType_rejectsAccessTokenWhenRefreshExpected() {
        String accessToken = jwtService.generateAccessToken("user-123", "test@example.com");
        Claims claims = jwtService.validateTokenOfType(accessToken, JwtService.TOKEN_TYPE_REFRESH);

        assertNull(claims, "Access token should not validate as refresh token");
    }

    @Test
    void validateTokenOfType_rejectsRefreshTokenWhenAccessExpected() {
        String refreshToken = jwtService.generateRefreshToken("user-123", "test@example.com");
        Claims claims = jwtService.validateTokenOfType(refreshToken, JwtService.TOKEN_TYPE_ACCESS);

        assertNull(claims, "Refresh token should not validate as access token");
    }

    @Test
    void validateToken_returnsNullForInvalidToken() {
        Claims claims = jwtService.validateToken("garbage.token.string");
        assertNull(claims);
    }

    @Test
    void validateToken_returnsNullForNull() {
        assertNull(jwtService.validateToken(null));
        assertNull(jwtService.validateToken(""));
    }

    @Test
    void differentTokens_haveDifferentJtis() {
        String token1 = jwtService.generateAccessToken("user-123", "test@example.com");
        String token2 = jwtService.generateAccessToken("user-123", "test@example.com");

        Claims claims1 = jwtService.parseToken(token1);
        Claims claims2 = jwtService.parseToken(token2);

        assertNotEquals(claims1.getId(), claims2.getId(),
                "Each token should have a unique JTI");
    }
}
