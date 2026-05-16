package com.networth.service;

import com.networth.model.entity.GmailConnection;
import com.networth.repository.GmailConnectionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailOAuthService {

    private final GmailConnectionRepository connectionRepository;

    @Value("${google.oauth.client-id:}")
    private String clientId;

    @Value("${google.oauth.client-secret:}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri:http://localhost:8080/api/v1/gmail/callback}")
    private String redirectUri;

    @Value("${token.encryption.key:}")
    private String encryptionKey;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        if (encryptionKey != null && !encryptionKey.isBlank()) {
            byte[] keyBytes = encryptionKey.length() == 32
                    ? encryptionKey.getBytes()
                    : java.util.Arrays.copyOf(encryptionKey.getBytes(), 32);
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        }
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    public String getAuthUrl() {
        if (!isConfigured()) return null;
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=https://www.googleapis.com/auth/gmail.readonly"
                + "&access_type=offline"
                + "&prompt=consent";
    }

    @Transactional
    public GmailConnection connect(UUID userId, String authCode) throws Exception {
        GoogleTokenResponse tokenResponse = exchangeCode(authCode);
        GmailConnection conn = connectionRepository.findByUserId(userId).orElse(null);
        if (conn == null) {
            conn = GmailConnection.builder().userId(userId).build();
        }
        conn.setGmailAddress(tokenResponse.email);
        conn.setAccessToken(encrypt(tokenResponse.accessToken));
        conn.setRefreshToken(encrypt(tokenResponse.refreshToken));
        conn.setTokenExpiry(LocalDateTime.now().plusSeconds(tokenResponse.expiresIn));
        conn.setSyncEnabled(true);
        return connectionRepository.save(conn);
    }

    @Transactional(readOnly = true)
    public Optional<GmailConnection> getConnection(UUID userId) {
        return connectionRepository.findByUserId(userId);
    }

    public String getDecryptedAccessToken(GmailConnection conn) {
        try {
            return decrypt(conn.getAccessToken());
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt access token", e);
        }
    }

    public String getDecryptedRefreshToken(GmailConnection conn) {
        try {
            return decrypt(conn.getRefreshToken());
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt refresh token", e);
        }
    }

    @Transactional
    public GmailConnection refreshAccessToken(GmailConnection conn) throws Exception {
        String refreshToken = getDecryptedRefreshToken(conn);
        GoogleTokenResponse tokenResponse = refreshToken(refreshToken);
        conn.setAccessToken(encrypt(tokenResponse.accessToken));
        conn.setTokenExpiry(LocalDateTime.now().plusSeconds(tokenResponse.expiresIn));
        return connectionRepository.save(conn);
    }

    @Transactional
    public void disconnect(UUID userId) {
        connectionRepository.findByUserId(userId).ifPresent(connectionRepository::delete);
    }

    private GoogleTokenResponse exchangeCode(String code) throws Exception {
        String body = "code=" + code
                + "&client_id=" + clientId
                + "&client_secret=" + clientSecret
                + "&redirect_uri=" + redirectUri
                + "&grant_type=authorization_code";
        return callGoogleTokenEndpoint(body);
    }

    private GoogleTokenResponse refreshToken(String refreshToken) throws Exception {
        String body = "client_id=" + clientId
                + "&client_secret=" + clientSecret
                + "&refresh_token=" + refreshToken
                + "&grant_type=refresh_token";
        return callGoogleTokenEndpoint(body);
    }

    private GoogleTokenResponse callGoogleTokenEndpoint(String body) throws Exception {
        java.net.URL url = new java.net.URL("https://oauth2.googleapis.com/token");
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes());
        }
        String response;
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream()))) {
            response = br.lines().collect(java.util.stream.Collectors.joining());
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var map = mapper.readValue(response, java.util.Map.class);
        GoogleTokenResponse tr = new GoogleTokenResponse();
        tr.accessToken = (String) map.get("access_token");
        tr.refreshToken = map.containsKey("refresh_token") ? (String) map.get("refresh_token") : null;
        tr.expiresIn = map.get("expires_in") instanceof Number
                ? ((Number) map.get("expires_in")).longValue() : 3600;
        tr.email = (String) map.get("email");
        if (tr.email == null) {
            tr.email = fetchEmail(tr.accessToken);
        }
        return tr;
    }

    private String fetchEmail(String accessToken) throws Exception {
        java.net.URL url = new java.net.URL("https://www.googleapis.com/oauth2/v2/userinfo");
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream()))) {
            String response = br.lines().collect(java.util.stream.Collectors.joining());
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var map = mapper.readValue(response, java.util.Map.class);
            return (String) map.get("email");
        }
    }

    private String encrypt(String plaintext) throws Exception {
        if (secretKey == null) return plaintext;
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    private String decrypt(String encrypted) throws Exception {
        if (secretKey == null) return encrypted;
        byte[] combined = Base64.getDecoder().decode(encrypted);
        byte[] iv = new byte[12];
        byte[] ciphertext = new byte[combined.length - 12];
        System.arraycopy(combined, 0, iv, 0, 12);
        System.arraycopy(combined, 12, ciphertext, 0, ciphertext.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext));
    }

    private static class GoogleTokenResponse {
        String accessToken;
        String refreshToken;
        long expiresIn;
        String email;
    }
}
