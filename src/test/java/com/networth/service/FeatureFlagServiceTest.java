package com.networth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Broker imports must not be advertised unless they can actually connect.
 *
 * <p>The flags defaulted to true while the OAuth credentials defaulted to empty, so the app
 * offered Upstox and Zerodha connections that failed the moment they were clicked.
 */
class FeatureFlagServiceTest {

    private FeatureFlagService withConfig(boolean upstoxFlag, String upstoxId, String upstoxSecret,
                                          boolean zerodhaFlag, String zerodhaKey, String zerodhaSecret) {
        FeatureFlagService service = new FeatureFlagService();
        ReflectionTestUtils.setField(service, "upstoxImport", upstoxFlag);
        ReflectionTestUtils.setField(service, "upstoxClientId", upstoxId);
        ReflectionTestUtils.setField(service, "upstoxClientSecret", upstoxSecret);
        ReflectionTestUtils.setField(service, "zerodhaImport", zerodhaFlag);
        ReflectionTestUtils.setField(service, "zerodhaApiKey", zerodhaKey);
        ReflectionTestUtils.setField(service, "zerodhaApiSecret", zerodhaSecret);
        return service;
    }

    @Test
    @DisplayName("flag on but no credentials: the broker is hidden")
    void flagWithoutCredentialsIsHidden() {
        FeatureFlagService service = withConfig(true, "", "", true, "", "");
        assertThat(service.isEnabled("upstox-import")).isFalse();
        assertThat(service.isEnabled("zerodha-import")).isFalse();
    }

    @Test
    @DisplayName("flag on with both credentials: the broker is offered")
    void flagWithCredentialsIsEnabled() {
        FeatureFlagService service = withConfig(true, "client-id", "secret", true, "api-key", "api-secret");
        assertThat(service.isEnabled("upstox-import")).isTrue();
        assertThat(service.isEnabled("zerodha-import")).isTrue();
    }

    @Test
    @DisplayName("half-configured credentials still count as unconfigured")
    void partialCredentialsAreNotEnough() {
        // An id without a secret cannot complete an OAuth exchange, so it must not show.
        assertThat(withConfig(true, "client-id", "", true, "api-key", "").isEnabled("upstox-import")).isFalse();
        assertThat(withConfig(true, "", "secret", true, "", "api-secret").isEnabled("zerodha-import")).isFalse();
    }

    @Test
    @DisplayName("blank-but-present credentials are treated as missing")
    void whitespaceCredentialsAreMissing() {
        assertThat(withConfig(true, "   ", "  ", true, " ", " ").isEnabled("upstox-import")).isFalse();
    }

    @Test
    @DisplayName("the flag still wins: credentials alone do not switch a broker on")
    void flagOffKeepsBrokerHidden() {
        FeatureFlagService service = withConfig(false, "client-id", "secret", false, "api-key", "api-secret");
        assertThat(service.isEnabled("upstox-import")).isFalse();
        assertThat(service.isEnabled("zerodha-import")).isFalse();
    }

    @Test
    @DisplayName("the brokers are gated independently of each other")
    void brokersGatedIndependently() {
        FeatureFlagService service = withConfig(true, "client-id", "secret", true, "", "");
        assertThat(service.isEnabled("upstox-import")).isTrue();
        assertThat(service.isEnabled("zerodha-import")).isFalse();
    }

    @Test
    @DisplayName("non-broker flags are unaffected by credential state")
    void otherFlagsUnaffected() {
        FeatureFlagService service = withConfig(true, "", "", true, "", "");
        ReflectionTestUtils.setField(service, "aiChat", true);
        ReflectionTestUtils.setField(service, "news", true);
        assertThat(service.isEnabled("ai-chat")).isTrue();
        assertThat(service.isEnabled("news")).isTrue();
        assertThat(service.getAllFlags()).containsKeys(
                "upstox-import", "zerodha-import", "ai-chat", "news", "dividends", "family-view", "gmail-sync");
    }
}
