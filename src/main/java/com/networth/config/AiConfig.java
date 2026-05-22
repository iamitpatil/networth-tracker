package com.networth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized configuration for all AI-related settings.
 * All values are configurable via application.properties with prefix "ai.".
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
@Getter
@Setter
public class AiConfig {

    // ── LLM Server ──────────────────────────────────────────
    /** Base URL for the llama.cpp / AI server */
    private String serverUrl = "http://localhost:8082/v1/chat/completions";

    /** Model name sent in the request body */
    private String model = "llama";

    /** LLM temperature (0.0 = deterministic, 1.0 = creative) */
    private double temperature = 0.1;

    /** HTTP connect timeout in milliseconds */
    private int connectTimeoutMs = 120000;

    /** HTTP read timeout in milliseconds */
    private int readTimeoutMs = 120000;

    // ── Tool Calling Loop ───────────────────────────────────
    /** Maximum number of AI tool-calling rounds per request */
    private int maxToolRounds = 3;

    /** Max characters for tool result before truncation (sent to LLM context) */
    private int toolResultMaxChars = 3000;

    // ── Sessions ────────────────────────────────────────────
    /** Max message pairs (user+assistant) per session before trimming */
    private int maxSessionMessages = 50;

    /** Pending action expiration in days */
    private int pendingActionExpiryDays = 7;

    // ── SSE Streaming ───────────────────────────────────────
    /** SseEmitter timeout in milliseconds */
    private long sseTimeoutMs = 180000;

    // ── Streaming Mode ──────────────────────────────────────
    /** Whether to use token-level streaming from the LLM (enables live reasoning) */
    private boolean streamTokens = true;
}
