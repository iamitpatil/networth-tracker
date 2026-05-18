package com.networth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class JwtService {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    // Default insecure key (must not be used in production)
    private static final String INSECURE_DEFAULT_KEY =
            "Y2hhbmdlLXRoaXMtdG8tYS1sb25nLXNlY3VyZS1rZXktaW4tcHJvZHVjdGlvbi1lbnZpcm9ubWVudA";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${jwt.issuer:networth-tracker}")
    private String issuer;

    private final Environment environment;

    public JwtService(Environment environment) {
        this.environment = environment;
    }

    /**
     * Fail-fast on startup if running with insecure default JWT secret in production.
     */
    @PostConstruct
    public void validateConfiguration() {
        boolean isProd = isProductionProfile();
        if (INSECURE_DEFAULT_KEY.equals(secret)) {
            if (isProd) {
                throw new IllegalStateException(
                        "FATAL: Default insecure JWT secret detected in production profile! " +
                        "Set JWT_SECRET environment variable to a strong random base64-encoded key.");
            } else {
                log.warn("WARNING: Using default insecure JWT secret. This is acceptable for dev but MUST be changed for production.");
            }
        }
        // Validate secret strength
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            if (keyBytes.length < 32) {
                String msg = "JWT secret too short: " + keyBytes.length + " bytes (minimum 32 bytes / 256 bits required)";
                if (isProd) {
                    throw new IllegalStateException(msg);
                } else {
                    log.warn(msg);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JWT secret: must be base64-encoded", e);
        }
    }

    private boolean isProductionProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("production".equalsIgnoreCase(profile) || "prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    public String generateAccessToken(String userId, String email) {
        return generateToken(userId, email, TOKEN_TYPE_ACCESS, accessTokenExpiration);
    }

    public String generateRefreshToken(String userId, String email) {
        return generateToken(userId, email, TOKEN_TYPE_REFRESH, refreshTokenExpiration);
    }

    private String generateToken(String userId, String email, String type, long expiration) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("type", type);

        return Jwts.builder()
                .subject(userId)
                .claims(claims)
                .id(UUID.randomUUID().toString())   // JTI for revocation
                .issuer(issuer)                      // Token issuer
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return parseToken(token).getSubject();
    }

    public String extractEmail(String token) {
        return parseToken(token).get("email", String.class);
    }

    public String extractTokenType(String token) {
        return parseToken(token).get("type", String.class);
    }

    public String extractJti(String token) {
        return parseToken(token).getId();
    }

    /**
     * Validates a token. Returns the claims if valid, or null if invalid.
     * Does NOT throw exceptions - safe to use in filters.
     */
    public Claims validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = parseToken(token);
            if (claims.getExpiration().before(new Date())) {
                return null;
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Validates token and checks it is the expected type (access or refresh).
     */
    public Claims validateTokenOfType(String token, String expectedType) {
        Claims claims = validateToken(token);
        if (claims == null) return null;
        String type = claims.get("type", String.class);
        if (!expectedType.equals(type)) {
            log.debug("Token type mismatch: expected '{}', got '{}'", expectedType, type);
            return null;
        }
        return claims;
    }

    public boolean isTokenValid(String token) {
        return validateToken(token) != null;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
