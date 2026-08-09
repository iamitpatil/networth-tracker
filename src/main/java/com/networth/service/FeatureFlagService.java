package com.networth.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple config-driven feature flags.
 * Each flag is a boolean property in application.properties:
 *   features.upstox-import=true
 *   features.zerodha-import=false
 *   features.ai-chat=true
 *
 * Frontend queries GET /api/v1/features to show/hide UI accordingly.
 */
@Service
@Slf4j
public class FeatureFlagService {

    @Value("${features.upstox-import:false}")
    private boolean upstoxImport;

    @Value("${features.zerodha-import:false}")
    private boolean zerodhaImport;

    @Value("${features.ai-chat:true}")
    private boolean aiChat;

    @Value("${features.news:true}")
    private boolean news;

    @Value("${features.dividends:true}")
    private boolean dividends;

    @Value("${features.family-view:true}")
    private boolean familyView;

    @Value("${features.gmail-sync:false}")
    private boolean gmailSync;

    // Broker OAuth credentials. A broker import is only genuinely available when these are
    // present, so they gate the flag rather than sitting unread.
    @Value("${broker.upstox.client-id:}")
    private String upstoxClientId;

    @Value("${broker.upstox.client-secret:}")
    private String upstoxClientSecret;

    @Value("${broker.zerodha.api-key:}")
    private String zerodhaApiKey;

    @Value("${broker.zerodha.api-secret:}")
    private String zerodhaApiSecret;

    /**
     * Logs which broker imports are switched on but unusable, so an operator can tell a
     * missing credential apart from a bug report about a vanished button.
     */
    @PostConstruct
    void reportBrokerReadiness() {
        if (upstoxImport && !isConfigured(upstoxClientId, upstoxClientSecret)) {
            log.warn("features.upstox-import is enabled but UPSTOX_CLIENT_ID/SECRET are not set - "
                    + "Upstox import will be hidden from the UI");
        }
        if (zerodhaImport && !isConfigured(zerodhaApiKey, zerodhaApiSecret)) {
            log.warn("features.zerodha-import is enabled but ZERODHA_API_KEY/SECRET are not set - "
                    + "Zerodha import will be hidden from the UI");
        }
    }

    /**
     * Feature flags as the UI sees them.
     *
     * <p>Broker imports require both the flag and configured credentials. The flags alone
     * defaulted to true while the credentials defaulted to empty, so the app advertised
     * Upstox and Zerodha connections that could only fail once clicked. Offering an action
     * that cannot succeed is worse than not offering it.
     */
    public Map<String, Boolean> getAllFlags() {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        flags.put("upstox-import", upstoxImport && isConfigured(upstoxClientId, upstoxClientSecret));
        flags.put("zerodha-import", zerodhaImport && isConfigured(zerodhaApiKey, zerodhaApiSecret));
        flags.put("ai-chat", aiChat);
        flags.put("news", news);
        flags.put("dividends", dividends);
        flags.put("family-view", familyView);
        flags.put("gmail-sync", gmailSync);
        return flags;
    }

    public boolean isEnabled(String feature) {
        return Boolean.TRUE.equals(getAllFlags().get(feature));
    }

    /** A credential pair counts as configured only when both halves are non-blank. */
    private static boolean isConfigured(String... credentials) {
        for (String credential : credentials) {
            if (credential == null || credential.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
