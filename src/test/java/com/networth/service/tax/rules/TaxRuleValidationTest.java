package com.networth.service.tax.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Moving tax rules out of Java and into a data file trades compile-time safety for
 * reviewability. These tests are the other half of that trade: they prove a malformed edit
 * stops the application at startup instead of quietly producing a wrong tax figure.
 */
class TaxRuleValidationTest {

    private static TaxRuleRegistry load(String fixture) {
        return new TaxRuleRegistry("taxrules/" + fixture);
    }

    @Test
    @DisplayName("the shipped rules file loads and validates")
    void shippedFileIsValid() {
        assertThat(new TaxRuleRegistry().supportedFinancialYears()).isNotEmpty();
    }

    @Test
    @DisplayName("slabs that do not ascend are rejected, naming the offending values")
    void slabsMustAscend() {
        assertThatThrownBy(() -> load("slabs-out-of-order.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must ascend");
    }

    @Test
    @DisplayName("the top slab must be open-ended, or income above it would go untaxed")
    void topSlabMustBeOpenEnded() {
        assertThatThrownBy(() -> load("slabs-not-open-ended.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("upTo=null");
    }

    @Test
    @DisplayName("a rate outside 0..1 is rejected - catches '20' entered for 20 percent")
    void rateMustBeAFraction() {
        assertThatThrownBy(() -> load("rate-out-of-range.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 0 and 1");
    }

    @Test
    @DisplayName("periods must be contiguous, so no date in the year falls through")
    void periodsMustBeContiguous() {
        assertThatThrownBy(() -> load("periods-with-gap.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contiguous");
    }

    @Test
    @DisplayName("the OLD regime is required in every year")
    void oldRegimeIsRequired() {
        assertThatThrownBy(() -> load("missing-old-regime.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OLD regime is required");
    }

    @Test
    @DisplayName("an unsupported format version is refused rather than half-read")
    void formatVersionIsChecked() {
        assertThatThrownBy(() -> load("unsupported-version.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("formatVersion 99");
    }

    @Test
    @DisplayName("a missing required field names both the year and the field")
    void missingFieldIsNamed() {
        assertThatThrownBy(() -> load("missing-field.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ltcgRate")
                .hasMessageContaining("2024-2025");
    }

    @Test
    @DisplayName("a missing rules file fails clearly rather than yielding an empty registry")
    void missingFileFailsClearly() {
        assertThatThrownBy(() -> load("does-not-exist.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot read tax rules");
    }
}
