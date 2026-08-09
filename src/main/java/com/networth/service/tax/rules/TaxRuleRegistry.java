package com.networth.service.tax.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TaxRegime;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The single source of truth for Indian tax rates and calculation rules, per financial year
 * and per regime, covering FY 2000-01 to FY 2026-27.
 *
 * <p>The data lives in {@code src/main/resources/tax-rules.json}, which carries its own
 * {@code _format} block describing every field. Keeping 27 years of slabs in a data file
 * rather than in Java makes it reviewable by someone who does not read Java — an accountant
 * can check it directly — and keeps a Budget change to a small, readable diff.
 *
 * <p>The risk with moving legal constants out of code is a silent typo. That is handled by
 * validating strictly here: the file is parsed and checked at startup, and anything malformed
 * (a slab out of order, a missing open-ended band, periods that do not cover the year, a rate
 * outside 0..1) throws and <b>fails the boot</b>. A bad edit stops the application rather than
 * quietly altering someone's tax computation. The tests in {@code TaxRuleRegistryTest} then
 * pin known figures for specific years.
 *
 * <h2>Verification status</h2>
 * Each year carries a {@link TaxRuleSet#verified()} flag. FY 2025-26 and FY 2026-27 were
 * checked against a published slab table. Earlier years are best-effort and flagged
 * unverified — the slab structures are reliable, but surcharge details and the exact year of
 * some transitions warrant a chartered accountant's review before they feed a real filing.
 * {@code GET /api/v1/tax/financial-years} exposes the flag.
 *
 * <h2>Eras worth knowing</h2>
 * <ul>
 *   <li><b>Equity LTCG</b> — 10% to FY 2003-04; <b>exempt</b> under s.10(38) from FY 2004-05
 *       to FY 2017-18 once STT applied; 10% over ₹1L under s.112A from FY 2018-19; 12.5% over
 *       ₹1.25L from 23 July 2024.</li>
 *   <li><b>Equity STCG</b> — slab rates before FY 2004-05; 10% under s.111A from FY 2004-05;
 *       15% from FY 2008-09; 20% from 23 July 2024.</li>
 *   <li><b>Cess</b> — none to FY 2003-04; 2% from FY 2004-05; 3% from FY 2007-08; 4% from
 *       FY 2018-19.</li>
 *   <li><b>New regime</b> — s.115BAC exists only from FY 2020-21. Asking for
 *       {@link TaxRegime#NEW} in an earlier year throws, which is correct.</li>
 * </ul>
 */
@Component
public class TaxRuleRegistry {

    private static final String RESOURCE = "tax-rules.json";
    private static final int SUPPORTED_FORMAT_VERSION = 1;

    /** Accepts the four-digit form the web UI sends and the two-digit Indian convention. */
    private static final Pattern FY_PATTERN = Pattern.compile("\\d{4}-(\\d{4}|\\d{2})");

    /** Canonical FY string -> effective periods, ordered by effectiveFrom. */
    private final Map<String, List<TaxRuleSet>> byFinancialYear;

    public TaxRuleRegistry() {
        this(RESOURCE);
    }

    /** Test seam: load an alternative classpath resource. */
    TaxRuleRegistry(String resourcePath) {
        this.byFinancialYear = Collections.unmodifiableMap(load(readResource(resourcePath), resourcePath));
    }

    private TaxRuleRegistry(JsonNode root, String label) {
        this.byFinancialYear = Collections.unmodifiableMap(load(root, label));
    }

    /**
     * Test seam: validate an in-memory rules document.
     *
     * <p>Lets the validation tests derive a deliberately malformed document from the real
     * {@code tax-rules.json} at runtime, so they cannot go stale when the schema gains a
     * field — a hand-written fixture would start failing for the wrong reason.
     */
    static TaxRuleRegistry fromJson(JsonNode root, String label) {
        return new TaxRuleRegistry(root, label);
    }

    // ── lookup ────────────────────────────────────────────────────────

    /**
     * Rules for a financial year. Where the year has several effective periods this returns
     * the last one, i.e. the rules in force at the end of the year. Prefer
     * {@link #forDate(LocalDate)} whenever a transaction date is available.
     */
    public TaxRuleSet forFinancialYear(String financialYear) {
        List<TaxRuleSet> periods = periodsFor(financialYear);
        return periods.get(periods.size() - 1);
    }

    /** All effective periods for a financial year, ordered. */
    public List<TaxRuleSet> periodsFor(String financialYear) {
        List<TaxRuleSet> periods = byFinancialYear.get(canonicalise(financialYear));
        if (periods == null) {
            throw new UnsupportedFinancialYearException(financialYear, byFinancialYear.keySet());
        }
        return periods;
    }

    /**
     * Rules in force on a specific date. This is what makes a mid-year rate change correct:
     * an equity disposal on 1 July 2024 is taxed differently from one on 1 August 2024.
     */
    public TaxRuleSet forDate(LocalDate date) {
        String fy = financialYearOf(date);
        for (TaxRuleSet period : periodsFor(fy)) {
            if (period.covers(date)) {
                return period;
            }
        }
        throw new IllegalStateException("Tax rules for " + fy + " do not cover " + date);
    }

    /** Financial years with rules defined, newest first — drives the API and the UI dropdown. */
    public List<String> supportedFinancialYears() {
        List<String> years = new ArrayList<>(byFinancialYear.keySet());
        years.sort(Comparator.reverseOrder());
        return years;
    }

    /** Financial years whose figures have not been checked against an authoritative source. */
    public List<String> unverifiedFinancialYears() {
        return supportedFinancialYears().stream()
                .filter(fy -> !forFinancialYear(fy).verified())
                .toList();
    }

    /**
     * Regimes available in a financial year. Before FY 2020-21 only the old regime existed,
     * so callers that offer a regime choice — or a comparison between the two — must check
     * this rather than assuming both are always present.
     */
    public Set<TaxRegime> regimesFor(String financialYear) {
        return forFinancialYear(financialYear).regimes().keySet();
    }

    /** Years where a regime comparison is meaningful, i.e. more than one regime existed. */
    public List<String> comparableFinancialYears() {
        return supportedFinancialYears().stream()
                .filter(fy -> regimesFor(fy).size() > 1)
                .toList();
    }

    public boolean supports(String financialYear) {
        try {
            return byFinancialYear.containsKey(canonicalise(financialYear));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ── financial year handling ───────────────────────────────────────

    /**
     * Normalises "2024-25" and "2024-2025" to the canonical "2024-2025".
     *
     * <p>The end year is derived as start + 1 rather than parsed. Parsing it turned "2024-25"
     * into the year 25 AD, which silently excluded every transaction and reported zero gains.
     */
    public String canonicalise(String financialYear) {
        int start = startYear(financialYear);
        return start + "-" + (start + 1);
    }

    public int startYear(String financialYear) {
        if (financialYear == null || !FY_PATTERN.matcher(financialYear.trim()).matches()) {
            throw new IllegalArgumentException("Invalid financial year '" + financialYear
                    + "'. Expected YYYY-YYYY (2024-2025) or YYYY-YY (2024-25).");
        }
        String[] parts = financialYear.trim().split("-");
        int start = Integer.parseInt(parts[0]);
        int end = Integer.parseInt(parts[1]);
        int expectedEnd = (parts[1].length() == 2) ? (start + 1) % 100 : start + 1;
        if (end != expectedEnd) {
            throw new IllegalArgumentException("Invalid financial year '" + financialYear
                    + "': must span consecutive years, e.g. " + start + "-" + (start + 1));
        }
        return start;
    }

    /** The Indian financial year containing a date. FY runs 1 April to 31 March. */
    public String financialYearOf(LocalDate date) {
        int start = (date.getMonthValue() >= 4) ? date.getYear() : date.getYear() - 1;
        return start + "-" + (start + 1);
    }

    /** The financial year in progress today. */
    public String currentFinancialYear() {
        return financialYearOf(LocalDate.now());
    }

    public LocalDate startOf(String financialYear) {
        return LocalDate.of(startYear(financialYear), 4, 1);
    }

    public LocalDate endOf(String financialYear) {
        return LocalDate.of(startYear(financialYear) + 1, 3, 31);
    }

    // ── loading and validation ────────────────────────────────────────

    private static JsonNode readResource(String resourcePath) {
        try (InputStream in = new ClassPathResource(resourcePath).getInputStream()) {
            return new ObjectMapper().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read tax rules from classpath:" + resourcePath, e);
        }
    }

    private Map<String, List<TaxRuleSet>> load(JsonNode root, String resourcePath) {
        int version = required(root, "formatVersion", resourcePath).asInt();
        if (version != SUPPORTED_FORMAT_VERSION) {
            throw new IllegalStateException(resourcePath + ": formatVersion " + version
                    + " is not supported (expected " + SUPPORTED_FORMAT_VERSION + ")");
        }

        JsonNode years = required(root, "financialYears", resourcePath);
        if (!years.isArray() || years.isEmpty()) {
            throw new IllegalStateException(resourcePath + ": financialYears must be a non-empty array");
        }

        Map<String, List<TaxRuleSet>> loaded = new LinkedHashMap<>();
        for (JsonNode yearNode : years) {
            String fy = canonicalise(text(yearNode, "financialYear", "financialYears[]"));
            if (loaded.containsKey(fy)) {
                throw new IllegalStateException(resourcePath + ": financial year " + fy + " is defined twice");
            }
            boolean verified = yearNode.path("verified").asBoolean(false);
            List<TaxRuleSet> periods = new ArrayList<>();
            JsonNode periodNodes = required(yearNode, "periods", fy);
            if (!periodNodes.isArray() || periodNodes.isEmpty()) {
                throw new IllegalStateException(fy + ": periods must be a non-empty array");
            }
            for (JsonNode p : periodNodes) {
                periods.add(parsePeriod(fy, verified, p));
            }
            periods.sort(Comparator.comparing(TaxRuleSet::effectiveFrom));
            validatePeriodsCoverYear(fy, periods);
            loaded.put(fy, List.copyOf(periods));
        }
        return loaded;
    }

    private TaxRuleSet parsePeriod(String fy, boolean verified, JsonNode p) {
        LocalDate from = LocalDate.parse(text(p, "effectiveFrom", fy));
        LocalDate to = LocalDate.parse(text(p, "effectiveTo", fy));
        if (to.isBefore(from)) {
            throw new IllegalStateException(fy + ": effectiveTo " + to + " precedes effectiveFrom " + from);
        }
        String where = fy + " [" + from + ".." + to + "]";

        JsonNode cgNode = required(p, "capitalGains", where);
        CapitalGainsRules capitalGains = new CapitalGainsRules(
                rate(cgNode, "ltcgRate", where),
                rate(cgNode, "stcgRate", where),
                amount(cgNode, "ltcgExemption", where),
                rate(cgNode, "cryptoRate", where),
                rate(cgNode, "otherAssetLtcgRate", where),
                parseThresholds(cgNode, where),
                required(cgNode, "defaultLongTermDays", where).asLong());

        JsonNode dNode = required(p, "deductions", where);
        DeductionLimits deductions = new DeductionLimits(
                amount(dNode, "limit80C", where),
                amount(dNode, "limit80CCD1B", where),
                amount(dNode, "limit80DSelf", where),
                amount(dNode, "limit80DParentsSenior", where));

        JsonNode regimeNodes = required(p, "regimes", where);
        Map<TaxRegime, RegimeRules> regimes = new EnumMap<>(TaxRegime.class);
        regimeNodes.fieldNames().forEachRemaining(name -> {
            TaxRegime regime;
            try {
                regime = TaxRegime.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(where + ": unknown regime '" + name + "'");
            }
            regimes.put(regime, parseRegime(where + " " + name, regimeNodes.get(name)));
        });
        if (!regimes.containsKey(TaxRegime.OLD)) {
            throw new IllegalStateException(where + ": the OLD regime is required");
        }

        return new TaxRuleSet(fy, from, to, capitalGains, Map.copyOf(regimes), deductions,
                rate(p, "cessRate", where), verified, p.path("note").asText(""));
    }

    private RegimeRules parseRegime(String where, JsonNode node) {
        List<Slab> slabs = new ArrayList<>();
        JsonNode slabNodes = required(node, "slabs", where);
        for (JsonNode s : slabNodes) {
            JsonNode upTo = s.path("upTo");
            slabs.add(new Slab(
                    upTo.isNull() || upTo.isMissingNode() ? null : new BigDecimal(upTo.asText()),
                    rate(s, "rate", where + " slab")));
        }
        validateSlabs(where, slabs);

        JsonNode rebateNode = required(node, "rebate", where);
        Rebate rebate = new Rebate(
                amount(rebateNode, "incomeThreshold", where),
                amount(rebateNode, "maxRebate", where));

        List<SurchargeBand> bands = new ArrayList<>();
        for (JsonNode b : required(node, "surchargeBands", where)) {
            bands.add(new SurchargeBand(amount(b, "incomeAbove", where), rate(b, "rate", where + " surcharge")));
        }
        for (int i = 1; i < bands.size(); i++) {
            if (bands.get(i).incomeAbove().compareTo(bands.get(i - 1).incomeAbove()) <= 0) {
                throw new IllegalStateException(where + ": surchargeBands must ascend by incomeAbove");
            }
        }

        JsonNode cap = node.path("surchargeCap");
        BigDecimal surchargeCap = cap.isNull() || cap.isMissingNode() ? null : new BigDecimal(cap.asText());

        return new RegimeRules(List.copyOf(slabs), amount(node, "standardDeduction", where),
                rebate, List.copyOf(bands), surchargeCap,
                required(node, "allowsDeductions", where).asBoolean());
    }

    /** Slabs must ascend and end with exactly one open-ended "and above" band. */
    private void validateSlabs(String where, List<Slab> slabs) {
        if (slabs.isEmpty()) {
            throw new IllegalStateException(where + ": slabs must not be empty");
        }
        for (int i = 0; i < slabs.size(); i++) {
            Slab slab = slabs.get(i);
            boolean last = (i == slabs.size() - 1);
            if (slab.isOpenEnded() != last) {
                throw new IllegalStateException(where + ": exactly one slab may have upTo=null and it must be last");
            }
            if (!last && slabs.get(i + 1).upTo() != null
                    && slab.upTo().compareTo(slabs.get(i + 1).upTo()) >= 0) {
                throw new IllegalStateException(where + ": slabs must ascend by upTo, found "
                        + slab.upTo() + " before " + slabs.get(i + 1).upTo());
            }
        }
    }

    /** Periods must be contiguous and together cover 1 April to 31 March exactly. */
    private void validatePeriodsCoverYear(String fy, List<TaxRuleSet> periods) {
        LocalDate expectedStart = startOf(fy);
        LocalDate expectedEnd = endOf(fy);
        if (!periods.get(0).effectiveFrom().equals(expectedStart)) {
            throw new IllegalStateException(fy + ": first period starts " + periods.get(0).effectiveFrom()
                    + ", expected " + expectedStart);
        }
        if (!periods.get(periods.size() - 1).effectiveTo().equals(expectedEnd)) {
            throw new IllegalStateException(fy + ": last period ends "
                    + periods.get(periods.size() - 1).effectiveTo() + ", expected " + expectedEnd);
        }
        for (int i = 1; i < periods.size(); i++) {
            LocalDate previousEnd = periods.get(i - 1).effectiveTo();
            LocalDate thisStart = periods.get(i).effectiveFrom();
            if (!thisStart.equals(previousEnd.plusDays(1))) {
                throw new IllegalStateException(fy + ": periods must be contiguous, but " + previousEnd
                        + " is followed by " + thisStart);
            }
        }
    }

    private Map<AssetType, Long> parseThresholds(JsonNode cgNode, String where) {
        Map<AssetType, Long> thresholds = new EnumMap<>(AssetType.class);
        JsonNode node = required(cgNode, "longTermThresholdDays", where);
        node.fieldNames().forEachRemaining(name -> {
            try {
                thresholds.put(AssetType.valueOf(name), node.get(name).asLong());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(where + ": unknown asset type '" + name
                        + "' in longTermThresholdDays");
            }
        });
        return Collections.unmodifiableMap(thresholds);
    }

    // ── parsing helpers: every failure names the year and the field ───

    private static JsonNode required(JsonNode parent, String field, String where) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new IllegalStateException(where + ": missing required field '" + field + "'");
        }
        return node;
    }

    private static String text(JsonNode parent, String field, String where) {
        return required(parent, field, where).asText();
    }

    /** Monetary amounts are strings so they parse as exact BigDecimal, never binary floats. */
    private static BigDecimal amount(JsonNode parent, String field, String where) {
        BigDecimal value = new BigDecimal(text(parent, field, where));
        if (value.signum() < 0) {
            throw new IllegalStateException(where + ": '" + field + "' must not be negative, got " + value);
        }
        return value;
    }

    private static BigDecimal rate(JsonNode parent, String field, String where) {
        BigDecimal value = new BigDecimal(text(parent, field, where));
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException(where + ": '" + field
                    + "' must be a fraction between 0 and 1, got " + value);
        }
        return value;
    }
}
