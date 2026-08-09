package com.networth.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.networth.model.entity.GmailConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailSyncService {

    private final GmailOAuthService oauthService;
    private final EmailParserService parserService;
    private final EmailTransactionService transactionService;
    private final com.networth.repository.GmailConnectionRepository connectionRepository;

    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String USER = "me";
    private static final int MAX_RESULTS = 20;

    @Scheduled(fixedRate = 300000)
    public void scheduledSync() {
        List<GmailConnection> connections = connectionRepository.findAll();
        for (GmailConnection conn : connections) {
            if (!Boolean.TRUE.equals(conn.getSyncEnabled())) continue;
            try {
                syncForConnection(conn);
            } catch (Exception e) {
                log.warn("Gmail sync failed for user {}: {}", conn.getUserId(), e.getMessage());
            }
        }
    }

    @Transactional
    public int syncForUser(UUID userId) throws Exception {
        GmailConnection conn = oauthService.getConnection(userId)
                .orElseThrow(() -> new IllegalArgumentException("Gmail not connected"));
        return syncForConnection(conn);
    }

    private int syncForConnection(GmailConnection conn) throws Exception {
        String accessToken = oauthService.getDecryptedAccessToken(conn);
        if (conn.getTokenExpiry() != null && conn.getTokenExpiry().isBefore(LocalDateTime.now())) {
            conn = oauthService.refreshAccessToken(conn);
            accessToken = oauthService.getDecryptedAccessToken(conn);
        }

        Gmail service = buildGmailService(accessToken);

        StringBuilder query = new StringBuilder("(");
        for (String sender : EmailParserService.BANK_SENDERS) {
            query.append("from:").append(sender).append(" OR ");
        }
        query.setLength(query.length() - 4);
        query.append(")");

        if (conn.getLastSyncAt() != null) {
            String since = String.format("%d/%d/%d",
                    conn.getLastSyncAt().getMonthValue(),
                    conn.getLastSyncAt().getDayOfMonth(),
                    conn.getLastSyncAt().getYear());
            query.append(" after:").append(since);
        }

        ListMessagesResponse response = service.users().messages().list(USER)
                .setQ(query.toString())
                .setMaxResults((long) MAX_RESULTS)
                .execute();

        List<Message> messages = response.getMessages();
        if (messages == null || messages.isEmpty()) {
            conn.setLastSyncAt(LocalDateTime.now());
            connectionRepository.save(conn);
            return 0;
        }

        int count = 0;
        for (Message msg : messages) {
            // One indexed existence check. This previously fetched the user's entire
            // email_transactions table on every iteration and scanned it in memory, so a sync of
            // 100 messages against 5,000 stored rows did half a million row comparisons.
            if (transactionService.alreadyIngested(conn.getUserId(), msg.getId())) {
                continue;
            }

            // Fetching the full message costs an API call, so it happens only after the cheap
            // duplicate check has ruled the message in.
            Message full = service.users().messages().get(USER, msg.getId()).setFormat("full").execute();
            String sender = getHeader(full, "From");
            String subject = getHeader(full, "Subject");
            String body = getBody(full);

            EmailParserService.ParsedEmail parsed = parserService.parse(sender, subject, body, full.getId());
            if (parsed.amount != null || parsed.balance != null) {
                transactionService.saveParsedEmail(parsed, conn.getUserId());
                count++;
            }
        }

        conn.setLastSyncAt(LocalDateTime.now());
        connectionRepository.save(conn);
        log.info("Synced {} bank emails for user {}", count, conn.getUserId());
        return count;
    }

    private Gmail buildGmailService(String accessToken) throws Exception {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(accessToken, null));
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);
        return new Gmail.Builder(transport, JSON_FACTORY, requestInitializer)
                .setApplicationName("networth-tracker")
                .build();
    }

    private String getHeader(Message msg, String name) {
        if (msg.getPayload() == null || msg.getPayload().getHeaders() == null) return "";
        return msg.getPayload().getHeaders().stream()
                .filter(h -> name.equalsIgnoreCase(h.getName()))
                .map(com.google.api.services.gmail.model.MessagePartHeader::getValue)
                .findFirst().orElse("");
    }

    private String getBody(Message msg) {
        try {
            if (msg.getPayload() == null) return "";
            String mimeType = msg.getPayload().getMimeType();
            byte[] data = null;

            if (msg.getPayload().getParts() != null) {
                for (var part : msg.getPayload().getParts()) {
                    if ("text/plain".equals(part.getMimeType()) && part.getBody() != null) {
                        data = part.getBody().getData() != null
                                ? Base64.getUrlDecoder().decode(part.getBody().getData())
                                : null;
                        if (data != null) break;
                    }
                }
            }

            if (data == null && msg.getPayload().getBody() != null) {
                data = msg.getPayload().getBody().getData() != null
                        ? Base64.getUrlDecoder().decode(msg.getPayload().getBody().getData())
                        : null;
            }

            if (data == null && msg.getPayload().getParts() != null) {
                for (var part : msg.getPayload().getParts()) {
                    if ("text/html".equals(part.getMimeType()) && part.getBody() != null) {
                        byte[] htmlData = part.getBody().getData() != null
                                ? Base64.getUrlDecoder().decode(part.getBody().getData())
                                : null;
                        if (htmlData != null) {
                            String html = new String(htmlData, java.nio.charset.StandardCharsets.UTF_8);
                            data = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim().getBytes();
                            break;
                        }
                    }
                }
            }

            return data != null ? new String(data, java.nio.charset.StandardCharsets.UTF_8) : "";
        } catch (Exception e) {
            log.warn("Failed to decode email body: {}", e.getMessage());
            return "";
        }
    }
}
