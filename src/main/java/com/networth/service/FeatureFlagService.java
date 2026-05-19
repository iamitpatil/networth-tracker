package com.networth.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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

    public Map<String, Boolean> getAllFlags() {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        flags.put("upstox-import", upstoxImport);
        flags.put("zerodha-import", zerodhaImport);
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
}
