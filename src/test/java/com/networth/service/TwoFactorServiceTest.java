package com.networth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TwoFactorServiceTest {

    private TwoFactorService twoFactorService;

    @BeforeEach
    void setUp() {
        twoFactorService = new TwoFactorService();
    }

    @Test
    void generateSecret_returnsValidBase32String() {
        String secret = twoFactorService.generateSecret();
        assertNotNull(secret);
        assertFalse(secret.isEmpty());
        // Base32 alphabet only
        assertTrue(secret.matches("[A-Z2-7]+"), "Secret should be base32: " + secret);
        // 160 bits = 20 bytes = 32 chars (160/5) in base32
        assertTrue(secret.length() >= 32, "Secret should be at least 32 chars");
    }

    @Test
    void generateOtpAuthUrl_containsRequiredFields() {
        String url = twoFactorService.generateOtpAuthUrl("ABCDEFGH", "user@example.com", "TestApp");
        assertTrue(url.startsWith("otpauth://totp/"));
        assertTrue(url.contains("secret=ABCDEFGH"));
        assertTrue(url.contains("issuer=TestApp"));
        assertTrue(url.contains("user@example.com"));
        assertTrue(url.contains("digits=6"));
        assertTrue(url.contains("period=30"));
    }

    @Test
    void verifyCode_acceptsCurrentCode() {
        String secret = twoFactorService.generateSecret();
        String currentCode = twoFactorService.getCurrentCode(secret);

        assertTrue(twoFactorService.verifyCode(secret, currentCode));
    }

    @Test
    void verifyCode_rejectsInvalidCode() {
        String secret = twoFactorService.generateSecret();
        assertFalse(twoFactorService.verifyCode(secret, "000000"));
        assertFalse(twoFactorService.verifyCode(secret, "999999"));
    }

    @Test
    void verifyCode_rejectsNullInputs() {
        assertFalse(twoFactorService.verifyCode(null, "123456"));
        assertFalse(twoFactorService.verifyCode("ABCDEFGH", null));
        assertFalse(twoFactorService.verifyCode(null, null));
    }

    @Test
    void verifyCode_rejectsInvalidFormat() {
        String secret = twoFactorService.generateSecret();
        assertFalse(twoFactorService.verifyCode(secret, "12345"));      // 5 digits
        assertFalse(twoFactorService.verifyCode(secret, "1234567"));    // 7 digits
        assertFalse(twoFactorService.verifyCode(secret, "abc123"));     // letters
        assertFalse(twoFactorService.verifyCode(secret, ""));           // empty
    }

    @Test
    void getCurrentCode_returns6DigitString() {
        String secret = twoFactorService.generateSecret();
        String code = twoFactorService.getCurrentCode(secret);

        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
    }
}
