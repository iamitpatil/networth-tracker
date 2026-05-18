package com.networth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Two-Factor Authentication service using TOTP (Time-based One-Time Password).
 * Implements RFC 6238 - compatible with Google Authenticator, Authy, etc.
 *
 * Algorithm: HMAC-SHA1 with 30-second time step, 6-digit codes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TwoFactorService {

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int SECRET_BYTES = 20; // 160 bits as per RFC

    // Allow ±1 time step (90 sec window) for clock drift
    private static final int TIME_STEP_TOLERANCE = 1;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a new secret for a user. Returns base32-encoded for QR code / manual entry.
     */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base32.encode(bytes);
    }

    /**
     * Generates an otpauth:// URL for QR code generation.
     */
    public String generateOtpAuthUrl(String secret, String userEmail, String issuer) {
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                issuer, userEmail, secret, issuer, CODE_DIGITS, TIME_STEP_SECONDS);
    }

    /**
     * Verifies a TOTP code against the given secret.
     * Allows ±1 time step tolerance for clock drift.
     */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || secret.isBlank() || code == null || code.isBlank()) {
            return false;
        }

        // Validate code format
        if (!code.matches("\\d{6}")) {
            return false;
        }

        long currentTimeStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        try {
            byte[] secretBytes = Base32.decode(secret);
            for (int i = -TIME_STEP_TOLERANCE; i <= TIME_STEP_TOLERANCE; i++) {
                String expectedCode = generateCode(secretBytes, currentTimeStep + i);
                if (constantTimeEquals(expectedCode, code)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Error verifying TOTP code: {}", e.getMessage());
            return false;
        }
        return false;
    }

    /**
     * Generates the current TOTP code for testing purposes.
     */
    public String getCurrentCode(String secret) {
        try {
            byte[] secretBytes = Base32.decode(secret);
            long timeStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
            return generateCode(secretBytes, timeStep);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP code", e);
        }
    }

    private String generateCode(byte[] secret, long timeStep) throws Exception {
        byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeStep).array();

        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
        byte[] hash = mac.doFinal(timeBytes);

        // Dynamic truncation per RFC 6238
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        int otp = binary % (int) Math.pow(10, CODE_DIGITS);
        return String.format("%0" + CODE_DIGITS + "d", otp);
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * Minimal Base32 encoder/decoder (RFC 4648).
     * TOTP standards use Base32 (not Base64) for QR-friendly secrets.
     */
    static class Base32 {
        private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

        public static String encode(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            int buffer = 0, bits = 0;
            for (byte b : bytes) {
                buffer = (buffer << 8) | (b & 0xFF);
                bits += 8;
                while (bits >= 5) {
                    int index = (buffer >> (bits - 5)) & 0x1F;
                    sb.append(ALPHABET.charAt(index));
                    bits -= 5;
                }
            }
            if (bits > 0) {
                int index = (buffer << (5 - bits)) & 0x1F;
                sb.append(ALPHABET.charAt(index));
            }
            return sb.toString();
        }

        public static byte[] decode(String encoded) {
            encoded = encoded.toUpperCase().replaceAll("[^A-Z2-7]", "");
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            int buffer = 0, bits = 0;
            for (char c : encoded.toCharArray()) {
                int index = ALPHABET.indexOf(c);
                if (index < 0) continue;
                buffer = (buffer << 5) | index;
                bits += 5;
                if (bits >= 8) {
                    baos.write((buffer >> (bits - 8)) & 0xFF);
                    bits -= 8;
                }
            }
            return baos.toByteArray();
        }
    }
}
