package com.networth.service.tax.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Moving tax rules out of Java and into a data file trades compile-time safety for
 * reviewability. These tests are the other half of that trade: they prove a malformed edit
 * stops the application at startup instead of quietly producing a wrong tax figure.
 *
 * <p>Each case is derived from the real {@code tax-rules.json} at runtime and then broken in
 * one specific way. Hand-written fixtures were used first and went stale the moment the schema
 * gained a field — they began failing on the missing field rather than the defect under test,
 * which is a test that lies about what it covers.
 */
class TaxRuleValidationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The shipped rules, mutated in one way, then validated. */
    private static TaxRuleRegistry loadBroken(Consumer<ObjectNode> breakIt) {
        ObjectNode root = readShippedRules();
        breakIt.accept(root);
        return TaxRuleRegistry.fromJson(root, "broken-rules.json");
    }

    private static ObjectNode readShippedRules() {
        try (InputStream in = TaxRuleValidationTest.class.getResourceAsStream("/tax-rules.json")) {
            ObjectNode root = (ObjectNode) MAPPER.readTree(in);
            // Keep one year so a failure names an unambiguous location.
            ArrayNode years = MAPPER.createArrayNode();
            for (JsonNode year : root.get("financialYears")) {
                if ("2024-2025".equals(year.get("financialYear").asText())) {
                    years.add(year);
                }
            }
            root.set("financialYears", years);
            return root;
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the shipped tax rules", e);
        }
    }

    private static ObjectNode firstPeriod(ObjectNode root) {
        return (ObjectNode) root.get("financialYears").get(0).get("periods").get(0);
    }

    private static ObjectNode oldRegime(ObjectNode root) {
        return (ObjectNode) firstPeriod(root).get("regimes").get("OLD");
    }

    @Test
    @DisplayName("the shipped rules file loads and validates")
    void shippedFileIsValid() {
        assertThat(new TaxRuleRegistry().supportedFinancialYears()).hasSize(27);
    }

    @Test
    @DisplayName("the derived baseline is itself valid, so a failure below is the injected defect")
    void baselineIsValid() {
        assertThat(loadBroken(root -> { }).supportedFinancialYears()).containsExactly("2024-2025");
    }

    @Test
    @DisplayName("slabs that do not ascend are rejected, naming the offending values")
    void slabsMustAscend() {
        assertThatThrownBy(() -> loadBroken(root -> {
            ArrayNode slabs = (ArrayNode) oldRegime(root).get("slabs");
            JsonNode first = slabs.get(0).deepCopy();
            slabs.set(0, slabs.get(1).deepCopy());
            slabs.set(1, first);
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("must ascend");
    }

    @Test
    @DisplayName("the top slab must be open-ended, or income above it would go untaxed")
    void topSlabMustBeOpenEnded() {
        assertThatThrownBy(() -> loadBroken(root -> {
            ArrayNode slabs = (ArrayNode) oldRegime(root).get("slabs");
            ((ObjectNode) slabs.get(slabs.size() - 1)).put("upTo", "99999999");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("upTo=null");
    }

    @Test
    @DisplayName("a rate outside 0..1 is rejected - catches '20' entered for 20 percent")
    void rateMustBeAFraction() {
        assertThatThrownBy(() -> loadBroken(root ->
                ((ObjectNode) firstPeriod(root).get("capitalGains")).put("stcgRate", "20")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("between 0 and 1");
    }

    @Test
    @DisplayName("periods must be contiguous, so no date in the year falls through")
    void periodsMustBeContiguous() {
        assertThatThrownBy(() -> loadBroken(root ->
                firstPeriod(root).put("effectiveTo", "2024-07-01")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("contiguous");
    }

    @Test
    @DisplayName("periods must cover the whole financial year")
    void periodsMustCoverTheYear() {
        assertThatThrownBy(() -> loadBroken(root ->
                firstPeriod(root).put("effectiveFrom", "2024-05-01")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("expected 2024-04-01");
    }

    @Test
    @DisplayName("the OLD regime is required in every year")
    void oldRegimeIsRequired() {
        assertThatThrownBy(() -> loadBroken(root ->
                ((ObjectNode) firstPeriod(root).get("regimes")).remove("OLD")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OLD regime is required");
    }

    @Test
    @DisplayName("an unsupported format version is refused rather than half-read")
    void formatVersionIsChecked() {
        assertThatThrownBy(() -> loadBroken(root -> root.put("formatVersion", 99)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("formatVersion 99");
    }

    @Test
    @DisplayName("a missing required field names both the year and the field")
    void missingFieldIsNamed() {
        assertThatThrownBy(() -> loadBroken(root ->
                ((ObjectNode) firstPeriod(root).get("capitalGains")).remove("ltcgRate")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ltcgRate")
                .hasMessageContaining("2024-2025");
    }

    @Test
    @DisplayName("a duplicated financial year is rejected")
    void duplicateYearRejected() {
        assertThatThrownBy(() -> loadBroken(root -> {
            ArrayNode years = (ArrayNode) root.get("financialYears");
            years.add(years.get(0).deepCopy());
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("defined twice");
    }

    @Test
    @DisplayName("a missing rules file fails clearly rather than yielding an empty registry")
    void missingFileFailsClearly() {
        assertThatThrownBy(() -> new TaxRuleRegistry("taxrules/does-not-exist.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot read tax rules");
    }
}
