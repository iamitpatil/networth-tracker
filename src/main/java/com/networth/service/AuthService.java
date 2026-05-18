package com.networth.service;

import com.networth.exception.ResourceNotFoundException;
import com.networth.model.dto.AuthResponse;
import com.networth.model.dto.LoginRequest;
import com.networth.model.dto.RegisterRequest;
import com.networth.model.entity.User;
import com.networth.repository.UserRepository;
import com.networth.security.JwtService;
import com.networth.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final TwoFactorService twoFactorService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .isActive(true)
                .twoFactorEnabled(false)
                .build();

        userRepository.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // Proper 2FA verification
        if (user.getTwoFactorEnabled() != null && user.getTwoFactorEnabled()) {
            if (request.getTwoFactorCode() == null || request.getTwoFactorCode().isBlank()) {
                throw new IllegalArgumentException("Two-factor authentication code required");
            }
            if (!twoFactorService.verifyCode(user.getTwoFactorSecret(), request.getTwoFactorCode())) {
                log.warn("Invalid 2FA code attempt for user {}", user.getId());
                throw new BadCredentialsException("Invalid two-factor authentication code");
            }
        }

        user.setLastLogin(java.time.LocalDateTime.now());
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    /**
     * Refresh access token using a valid refresh token.
     * Implements refresh token rotation: the old refresh token is revoked,
     * a new refresh token is issued. This prevents token replay attacks.
     */
    public AuthResponse refreshToken(String refreshToken) {
        // Strictly validate that this is a refresh token (not an access token)
        Claims claims = jwtService.validateTokenOfType(refreshToken, JwtService.TOKEN_TYPE_REFRESH);
        if (claims == null) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        // Check if token has been revoked
        String jti = claims.getId();
        if (jti != null && tokenBlacklistService.isRevoked(jti)) {
            log.warn("Attempted to use revoked refresh token: {}", jti);
            throw new BadCredentialsException("Refresh token has been revoked");
        }

        String userIdStr = claims.getSubject();
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (user.getIsActive() != null && !user.getIsActive()) {
            throw new BadCredentialsException("Account is disabled");
        }

        // Revoke the old refresh token (rotation)
        if (jti != null && claims.getExpiration() != null) {
            tokenBlacklistService.revokeToken(jti, claims.getExpiration().getTime() / 1000);
        }

        return buildAuthResponse(user);
    }

    /**
     * Logout: revoke the current access token.
     */
    public void logout(String accessToken) {
        Claims claims = jwtService.validateToken(accessToken);
        if (claims != null && claims.getId() != null && claims.getExpiration() != null) {
            tokenBlacklistService.revokeToken(claims.getId(), claims.getExpiration().getTime() / 1000);
            log.info("User {} logged out, token revoked", claims.getSubject());
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.builder()
                .accessToken(jwtService.generateAccessToken(user.getId().toString(), user.getEmail()))
                .refreshToken(jwtService.generateRefreshToken(user.getId().toString(), user.getEmail()))
                .userId(user.getId().toString())
                .email(user.getEmail())
                .name(user.getName())
                .twoFactorEnabled(user.getTwoFactorEnabled() != null && user.getTwoFactorEnabled())
                .build();
    }
}
