package com.networth.service.tax.rules;

import com.networth.model.enums.AssetType;
import com.networth.model.enums.TaxRegime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxRuleRegistryTest {

    private final TaxRuleRegistry registry = new TaxRuleRegistry();

    // ── financial year handling ───────────────────────────────────────

    @Test
    @DisplayName("both the four-digit and Indian two-digit FY forms canonicalise identically")
    void bothFinancialYearFormatsAccepted() {
        assertThat(registry.canonicalise("2024-2025")).isEqualTo("2024-2025");
        assertThat(registry.canonicalise("2024-25")).isEqualTo("2024-2025");
        assertThat(registry.forFinancialYear("2024-25"))
                .isEqualTo(registry.forFinancialYear("2024-2025"));
    }

    @Test
    @DisplayName("the end year is derived, never parsed - '2024-25' must not become year 25 AD")
    void endYearIsDerivedNotParsed() {
        assertThat(registry.endOf("2024-25")).isEqualTo(LocalDate.of(2025, 3, 31));
        assertThat(registry.startOf("2024-25")).isEqualTo(LocalDate.of(2024, 4, 1));
    }

    @Test
    @DisplayName("an unparseable or non-consecutive financial year is rejected")
    void invalidFinancialYearRejected() {
        assertThatThrownBy(() -> registry.startYear("2024"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected YYYY-YYYY");
        assertThatThrownBy(() -> registry.startYear("2024-2030"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consecutive");
        assertThatThrownBy(() -> registry.startYear(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a year with no rules fails loudly and names the supported years")
    void unsupportedYearNamesSupportedYears() {
        assertThatThrownBy(() -> registry.forFinancialYear("1999-2000"))
                .isInstanceOf(UnsupportedFinancialYearException.class)
                .hasMessageContaining("1999-2000")
                .hasMessageContaining("2024-2025");
        assertThat(registry.supports("1999-2000")).isFalse();
        assertThat(registry.supports("2030-2031")).isFalse();
        assertThat(registry.supports("2024-25")).isTrue();
    }

    @Test
    @DisplayName("every year from FY 2000-01 to FY 2026-27 is defined, newest first")
    void allYearsFrom2000Defined() {
        List<String> years = registry.supportedFinancialYears();
        assertThat(years).hasSize(27);
        assertThat(years.get(0)).isEqualTo("2026-2027");
        assertThat(years.get(years.size() - 1)).isEqualTo("2000-2001");
        // No gaps: each year is one less than the previous.
        for (int i = 1; i < years.size(); i++) {
            assertThat(registry.startYear(years.get(i)))
                    .isEqualTo(registry.startYear(years.get(i - 1)) - 1);
        }
    }

    @Test
    @DisplayName("every defined year resolves for every date it covers")
    void everyYearResolvesForItsDates() {
        for (String fy : registry.supportedFinancialYears()) {
            assertThat(registry.forDate(registry.startOf(fy)).financialYear()).isEqualTo(fy);
            assertThat(registry.forDate(registry.endOf(fy)).financialYear()).isEqualTo(fy);
        }
    }

    @Test
    @DisplayName("the financial year of a date respects the 1 April boundary")
    void financialYearOfDate() {
        assertThat(registry.financialYearOf(LocalDate.of(2024, 3, 31))).isEqualTo("2023-2024");
        assertThat(registry.financialYearOf(LocalDate.of(2024, 4, 1))).isEqualTo("2024-2025");
        assertThat(registry.financialYearOf(LocalDate.of(2025, 3, 31))).isEqualTo("2024-2025");
    }

    // ── the 23 July 2024 mid-year split ───────────────────────────────

    @Test
    @DisplayName("FY 2024-25 has two effective periods split on 23 July 2024")
    void fy2024HasTwoPeriods() {
        List<TaxRuleSet> periods = registry.periodsFor("2024-2025");
        assertThat(periods).hasSize(2);
        assertThat(periods.get(0).effectiveTo()).isEqualTo(LocalDate.of(2024, 7, 22));
        assertThat(periods.get(1).effectiveFrom()).isEqualTo(LocalDate.of(2024, 7, 23));
    }

    @Test
    @DisplayName("equity rates before 23 July 2024 are 10% / 15% with a 1L exemption")
    void preJuly2024EquityRates() {
        CapitalGainsRules cg = registry.forDate(LocalDate.of(2024, 7, 1)).capitalGains();
        assertThat(cg.ltcgRate()).isEqualByComparingTo("0.10");
        assertThat(cg.stcgRate()).isEqualByComparingTo("0.15");
        assertThat(cg.ltcgExemption()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("equity rates from 23 July 2024 are 12.5% / 20% with a 1.25L exemption")
    void postJuly2024EquityRates() {
        // Boundary day itself must use the new rates.
        CapitalGainsRules onTheDay = registry.forDate(LocalDate.of(2024, 7, 23)).capitalGains();
        assertThat(onTheDay.ltcgRate()).isEqualByComparingTo("0.125");
        assertThat(onTheDay.stcgRate()).isEqualByComparingTo("0.20");
        assertThat(onTheDay.ltcgExemption()).isEqualByComparingTo("125000");

        CapitalGainsRules later = registry.forDate(LocalDate.of(2024, 8, 1)).capitalGains();
        assertThat(later.ltcgRate()).isEqualByComparingTo("0.125");
    }

    @Test
    @DisplayName("forFinancialYear returns the period in force at year end")
    void forFinancialYearReturnsLatestPeriod() {
        assertThat(registry.forFinancialYear("2024-2025").capitalGains().ltcgRate())
                .isEqualByComparingTo("0.125");
    }

    // ── holding periods ───────────────────────────────────────────────

    @Test
    @DisplayName("long-term thresholds differ by asset type, and crypto has none")
    void longTermThresholds() {
        CapitalGainsRules cg = registry.forFinancialYear("2024-2025").capitalGains();
        assertThat(cg.isLongTerm(AssetType.EQUITY, 365)).isTrue();
        assertThat(cg.isLongTerm(AssetType.EQUITY, 364)).isFalse();
        assertThat(cg.isLongTerm(AssetType.GOLD, 1095)).isTrue();
        assertThat(cg.isLongTerm(AssetType.GOLD, 1094)).isFalse();
        // Crypto is taxed at a flat rate regardless of holding period.
        assertThat(cg.isLongTerm(AssetType.CRYPTO, 1)).isTrue();
    }

    // ── regimes ───────────────────────────────────────────────────────

    @Test
    @DisplayName("FY 2024-25 new regime: 3L nil band, 75k standard deduction, 7L rebate cliff")
    void fy2024NewRegime() {
        RegimeRules r = registry.forFinancialYear("2024-2025").regime(TaxRegime.NEW);
        assertThat(r.standardDeduction()).isEqualByComparingTo("75000");
        assertThat(r.rebate().incomeThreshold()).isEqualByComparingTo("700000");
        assertThat(r.rebate().maxRebate()).isEqualByComparingTo("25000");
        assertThat(r.slabs().get(0).upTo()).isEqualByComparingTo("300000");
        assertThat(r.slabs().get(r.slabs().size() - 1).isOpenEnded()).isTrue();
        assertThat(r.allowsDeductions()).isFalse();
    }

    @Test
    @DisplayName("FY 2024-25 old regime: 2.5L nil band, 50k standard deduction, deductions allowed")
    void fy2024OldRegime() {
        RegimeRules r = registry.forFinancialYear("2024-2025").regime(TaxRegime.OLD);
        assertThat(r.standardDeduction()).isEqualByComparingTo("50000");
        assertThat(r.rebate().maxRebate()).isEqualByComparingTo("12500");
        assertThat(r.allowsDeductions()).isTrue();
    }

    @Test
    @DisplayName("FY 2025-26 new regime has the Budget 2025 slabs and the 12L rebate")
    void fy2025NewRegime() {
        RegimeRules r = registry.forFinancialYear("2025-2026").regime(TaxRegime.NEW);
        assertThat(r.slabs().get(0).upTo()).isEqualByComparingTo("400000");
        assertThat(r.slabs()).hasSize(7);
        assertThat(r.rebate().incomeThreshold()).isEqualByComparingTo("1200000");
    }

    @Test
    @DisplayName("surcharge is capped at 25% in the new regime but reaches 37% in the old")
    void surchargeCapDiffersByRegime() {
        TaxRuleSet rules = registry.forFinancialYear("2024-2025");
        RegimeRules oldRegime = rules.regime(TaxRegime.OLD);
        RegimeRules newRegime = rules.regime(TaxRegime.NEW);

        assertThat(oldRegime.surchargeRateFor(new BigDecimal("60000000"))).isEqualByComparingTo("0.37");
        assertThat(newRegime.surchargeRateFor(new BigDecimal("60000000"))).isEqualByComparingTo("0.25");
        // Lower bands are identical across regimes.
        assertThat(newRegime.surchargeRateFor(new BigDecimal("6000000"))).isEqualByComparingTo("0.10");
        assertThat(newRegime.surchargeRateFor(new BigDecimal("4000000"))).isEqualByComparingTo("0");
    }

    // ── deductions ────────────────────────────────────────────────────

    @Test
    @DisplayName("deduction ceilings are exposed per year")
    void deductionLimits() {
        DeductionLimits d = registry.forFinancialYear("2024-2025").deductions();
        assertThat(d.limit80C()).isEqualByComparingTo("150000");
        assertThat(d.limit80CCD1B()).isEqualByComparingTo("50000");
    }

    // ── provenance ────────────────────────────────────────────────────

    @Test
    @DisplayName("verification status is explicit: recent years checked, older years flagged")
    void verificationStatusIsExplicit() {
        // Checked against a published slab table.
        assertThat(registry.forFinancialYear("2025-2026").verified()).isTrue();
        assertThat(registry.forFinancialYear("2026-2027").verified()).isTrue();
        // Older years are best-effort and must advertise that.
        assertThat(registry.forFinancialYear("2005-2006").verified()).isFalse();
        assertThat(registry.unverifiedFinancialYears())
                .contains("2005-2006", "2024-2025")
                .doesNotContain("2025-2026", "2026-2027");
        // Every year carries provenance so a reviewer knows what they are looking at.
        for (String fy : registry.supportedFinancialYears()) {
            assertThat(registry.forFinancialYear(fy).note()).as("note for " + fy).isNotBlank();
        }
    }

    // ── historical eras ───────────────────────────────────────────────

    @Test
    @DisplayName("equity LTCG was exempt under s.10(38) from FY 2004-05 to FY 2017-18")
    void equityLtcgExemptEra() {
        for (String fy : List.of("2004-2005", "2010-2011", "2017-2018")) {
            assertThat(registry.forFinancialYear(fy).capitalGains().ltcgRate())
                    .as("LTCG rate for " + fy).isEqualByComparingTo("0");
        }
        // Taxable either side of that era.
        assertThat(registry.forFinancialYear("2003-2004").capitalGains().ltcgRate())
                .isEqualByComparingTo("0.10");
        assertThat(registry.forFinancialYear("2018-2019").capitalGains().ltcgRate())
                .isEqualByComparingTo("0.10");
    }

    @Test
    @DisplayName("equity STCG went 10% to 15% in FY 2008-09 under s.111A")
    void equityStcgStepUp() {
        assertThat(registry.forFinancialYear("2007-2008").capitalGains().stcgRate())
                .isEqualByComparingTo("0.10");
        assertThat(registry.forFinancialYear("2008-2009").capitalGains().stcgRate())
                .isEqualByComparingTo("0.15");
    }

    @Test
    @DisplayName("cess progressed nil, 2%, 3%, 4% across the eras")
    void cessProgression() {
        assertThat(registry.forFinancialYear("2003-2004").cessRate()).isEqualByComparingTo("0");
        assertThat(registry.forFinancialYear("2004-2005").cessRate()).isEqualByComparingTo("0.02");
        assertThat(registry.forFinancialYear("2007-2008").cessRate()).isEqualByComparingTo("0.03");
        assertThat(registry.forFinancialYear("2018-2019").cessRate()).isEqualByComparingTo("0.04");
    }

    @Test
    @DisplayName("the new regime exists only from FY 2020-21; asking earlier is an error")
    void newRegimeOnlyFrom2020() {
        assertThatThrownBy(() -> registry.forFinancialYear("2019-2020").regime(TaxRegime.NEW))
                .isInstanceOf(RegimeNotAvailableException.class)
                .hasMessageContaining("115BAC");
        assertThat(registry.forFinancialYear("2020-2021").regime(TaxRegime.NEW)).isNotNull();
        // The old regime is available in every year.
        for (String fy : registry.supportedFinancialYears()) {
            assertThat(registry.forFinancialYear(fy).regime(TaxRegime.OLD)).as("OLD for " + fy).isNotNull();
        }
    }

    @Test
    @DisplayName("s.87A rebate did not exist before FY 2013-14")
    void rebateIntroducedIn2013() {
        assertThat(registry.forFinancialYear("2012-2013").regime(TaxRegime.OLD).rebate().maxRebate())
                .isEqualByComparingTo("0");
        assertThat(registry.forFinancialYear("2013-2014").regime(TaxRegime.OLD).rebate().maxRebate())
                .isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("the 80C ceiling rose from 1L to 1.5L in FY 2014-15")
    void eightyCLimitRaised() {
        assertThat(registry.forFinancialYear("2013-2014").deductions().limit80C())
                .isEqualByComparingTo("100000");
        assertThat(registry.forFinancialYear("2014-2015").deductions().limit80C())
                .isEqualByComparingTo("150000");
    }
}
