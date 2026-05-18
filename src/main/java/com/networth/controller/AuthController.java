package com.networth.controller;

import com.networth.model.dto.AuthResponse;
import com.networth.model.dto.LoginRequest;
import com.networth.model.dto.RefreshTokenRequest;
import com.networth.model.dto.RegisterRequest;
import com.networth.model.entity.User;
import com.networth.repository.UserRepository;
import com.networth.service.AuthService;
import com.networth.service.TwoFactorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TwoFactorService twoFactorService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Setup 2FA: Returns a secret and QR code URL for the user to scan with their authenticator app.
     * The user must then call /verify-setup with a code from their app to enable 2FA.
     */
    @PostMapping("/2fa/setup")
    public ResponseEntity<Map<String, String>> setup2FA(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String secret = twoFactorService.generateSecret();
        String otpAuthUrl = twoFactorService.generateOtpAuthUrl(secret, user.getEmail(), "NetWorth Tracker");

        // Store secret but don't enable yet - user must verify
        user.setTwoFactorSecret(secret);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "secret", secret,
                "qrCodeUrl", otpAuthUrl
        ));
    }

    /**
     * Verify 2FA setup with a code from the authenticator app.
     * On success, 2FA is enabled for the user.
     */
    @PostMapping("/2fa/verify-setup")
    public ResponseEntity<Map<String, String>> verifySetup2FA(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code is required"));
        }

        UUID userId = UUID.fromString(userDetails.getUsername());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getTwoFactorSecret() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "2FA setup not initiated. Call /2fa/setup first."));
        }

        if (!twoFactorService.verifyCode(user.getTwoFactorSecret(), code)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid code"));
        }

        user.setTwoFactorEnabled(true);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Two-factor authentication enabled"));
    }

    /**
     * Disable 2FA. Requires the user to provide a current 2FA code to disable.
     */
    @PostMapping("/2fa/disable")
    public ResponseEntity<Map<String, String>> disable2FA(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code is required"));
        }

        UUID userId = UUID.fromString(userDetails.getUsername());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getTwoFactorEnabled() == null || !user.getTwoFactorEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("error", "2FA is not enabled"));
        }

        if (!twoFactorService.verifyCode(user.getTwoFactorSecret(), code)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid code"));
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Two-factor authentication disabled"));
    }
}
