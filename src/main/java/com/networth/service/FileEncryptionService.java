package com.networth.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-GCM file encryption service for documents at rest.
 *
 * File format: [12-byte IV][AES-GCM ciphertext + 16-byte auth tag]
 *
 * The encryption key is derived from the configured secret via SHA-256.
 * If no key is configured, encryption is disabled (passthrough).
 */
@Service
@Slf4j
public class FileEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    @Value("${app.encryption.key:}")
    private String encryptionKey;

    private SecretKey secretKey;
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (encryptionKey != null && !encryptionKey.isBlank()) {
            try {
                // Derive a 256-bit key from the configured secret via SHA-256
                byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                        .digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
                secretKey = new SecretKeySpec(keyBytes, "AES");
                enabled = true;
                log.info("File encryption enabled (AES-256-GCM)");
            } catch (Exception e) {
                log.error("Failed to initialize file encryption: {}", e.getMessage());
                enabled = false;
            }
        } else {
            enabled = false;
            log.warn("File encryption disabled — set app.encryption.key to enable");
        }
    }

    /**
     * Whether encryption is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Encrypt file bytes and write to the target path.
     * Format: [12-byte IV][ciphertext + auth tag]
     */
    public void encryptAndWrite(byte[] plainBytes, Path targetPath) throws Exception {
        if (!enabled) {
            Files.write(targetPath, plainBytes);
            return;
        }

        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plainBytes);

        // Write IV + ciphertext
        try (OutputStream os = Files.newOutputStream(targetPath)) {
            os.write(iv);
            os.write(ciphertext);
        }
    }

    /**
     * Encrypt from an InputStream and write to the target path.
     */
    public void encryptAndWrite(InputStream inputStream, Path targetPath) throws Exception {
        encryptAndWrite(inputStream.readAllBytes(), targetPath);
    }

    /**
     * Read and decrypt file from the given path.
     * Returns the decrypted bytes.
     */
    public byte[] readAndDecrypt(Path filePath) throws Exception {
        if (!enabled) {
            return Files.readAllBytes(filePath);
        }

        byte[] fileBytes = Files.readAllBytes(filePath);
        if (fileBytes.length < IV_LENGTH) {
            throw new IllegalStateException("Encrypted file too short: " + filePath);
        }

        byte[] iv = Arrays.copyOfRange(fileBytes, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(fileBytes, IV_LENGTH, fileBytes.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    /**
     * Read and decrypt file, returning an InputStream.
     */
    public InputStream readAndDecryptAsStream(Path filePath) throws Exception {
        return new ByteArrayInputStream(readAndDecrypt(filePath));
    }
}
