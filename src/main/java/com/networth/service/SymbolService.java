package com.networth.service;

import com.networth.model.entity.Symbol;
import com.networth.repository.SymbolRepository;
import com.networth.service.market.NpsNavService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The reference lists every ticker is validated against, and where they come from.
 *
 * <p>Each source is split into a <em>parser</em> — HTTP plus format-specific decoding, which is the
 * fragile part and exists once per source — and a shared <em>persister</em>. The split is what makes
 * "load the missing ones, skip the ones already there" possible: parsing is unchanged either way,
 * only the write differs. See {@link Mode}.
 *
 * <p>Populated before the application accepts traffic by {@link SymbolBootstrapService}, and
 * refreshable on demand through {@code POST /api/v1/symbols/refresh}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SymbolService {

    private final SymbolRepository symbolRepository;
    private final com.networth.service.portfolio.HoldingService holdingService;
    private final NpsNavService npsNavService;
    private final JdbcTemplate jdbcTemplate;

    private static final String NSE_CSV_URL = "https://archives.nseindia.com/content/equities/EQUITY_L.csv";
    private static final String NSE_ETF_CSV_URL = "https://nsearchives.nseindia.com/content/equities/eq_etfseclist.csv";
    private static final String NSE_DEBT_CSV_URL = "https://archives.nseindia.com/content/equities/DEBT.csv";
    private static final String MF_NAV_URL = "https://portal.amfiindia.com/spages/NAVAll.txt";

    /**
     * NSE's archive CSVs are Windows-1252, not UTF-8.
     *
     * <p>The ETF list contains a raw {@code 0x96} (en dash) in three security names, and Java's UTF-8
     * decoder does not throw on that — it substitutes U+FFFD, so the corruption arrives silently and
     * ends up in the dropdown. Windows-1252 is an ASCII superset, so every clean row decodes
     * identically and the three affected names come out right.
     */
    private static final Charset NSE_CHARSET = Charset.forName("windows-1252");

    /** Rows per JDBC batch. Large enough to amortise the round trip, small enough to keep memory flat. */
    private static final int BATCH_SIZE = 1000;

    // Column widths from V7/V18/V19. A row that cannot fit its primary key is unstorable and is
    // dropped with a warning; a long name or sector is truncated, because losing the instrument over
    // a cosmetic field would be worse.
    private static final int MAX_SYMBOL = 20;
    private static final int MAX_NAME = 255;
    private static final int MAX_SECTOR = 100;
    private static final int MAX_SCHEME_CODE = 10;
    private static final int ISIN_LENGTH = 12;

    /** A reference list, and the {@code symbols.category} its rows land in. */
    public enum Source {
        EQUITY("EQUITY"),
        ETF("ETF"),
        BOND("BOND"),
        MUTUAL_FUND("MUTUAL_FUND"),
        NPS("NPS");

        private final String category;

        Source(String category) {
            this.category = category;
        }

        public String category() {
            return category;
        }
    }

    /**
     * How a parsed list is written.
     *
     * <p>{@code INSERT_MISSING} adds what the table does not have and leaves everything else
     * untouched, in one batched statement per thousand rows. "Already present" is decided by
     * {@code symbols_pkey} rather than by a read-then-write per row, so there is no race and no
     * 22,000-SELECT warm-up. {@code UPSERT} additionally corrects a changed name or a
     * late-published ISIN on rows that already exist, which is what a manual refresh is for.
     */
    public enum Mode { INSERT_MISSING, UPSERT }

    private static final String INSERT_SYMBOL_SQL = """
            INSERT INTO symbols (symbol, name, category, sector, isin, scheme_code, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (symbol) DO NOTHING
            """;

    /**
     * The same insert, allowed to correct a row that already exists.
     *
     * <p>{@code category} is deliberately left alone — a row's classification is not something a
     * refresh should silently change under a holding that was validated against it. {@code isin} and
     * {@code scheme_code} are {@code COALESCE}d so a source that stops publishing an identifier it
     * once published cannot blank the one holdings were resolved against.
     */
    private static final String UPSERT_SYMBOL_SQL = """
            INSERT INTO symbols (symbol, name, category, sector, isin, scheme_code, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (symbol) DO UPDATE SET
                name        = EXCLUDED.name,
                sector      = EXCLUDED.sector,
                isin        = COALESCE(EXCLUDED.isin, symbols.isin),
                scheme_code = COALESCE(EXCLUDED.scheme_code, symbols.scheme_code),
                updated_at  = EXCLUDED.updated_at
            """;

    private static final String INSERT_ALIAS_SQL = """
            INSERT INTO symbol_aliases (symbol, source, alias)
            VALUES (?, ?, ?)
            ON CONFLICT (symbol, source) DO NOTHING
            """;

    private static final String UPSERT_ALIAS_SQL = """
            INSERT INTO symbol_aliases (symbol, source, alias)
            VALUES (?, ?, ?)
            ON CONFLICT (symbol, source) DO UPDATE SET alias = EXCLUDED.alias
            """;

    // ── the public surface ────────────────────────────────────────────

    /**
     * Fetch one reference list and write it.
     *
     * <p>Never throws. A provider that 403s, times out or returns nothing is logged and reported as
     * zero rows: the pre-start loader must still let the application boot, and a manual refresh must
     * still report on the sources that did answer.
     *
     * @return how many rows were added ({@code INSERT_MISSING}) or written ({@code UPSERT})
     */
    @Transactional
    public int load(Source source, Mode mode) {
        List<Symbol> parsed;
        try {
            parsed = switch (source) {
                case EQUITY -> parseNseEquities();
                case ETF -> parseNseEtfs();
                case BOND -> parseNseBonds();
                case MUTUAL_FUND -> parseAmfiFunds();
                case NPS -> parseNpsSchemes();
            };
        } catch (Exception e) {
            log.error("Could not fetch the {} reference list: {}", source, e.getMessage());
            return 0;
        }

        if (parsed.isEmpty()) {
            log.warn("The {} reference list came back empty — nothing written", source);
            return 0;
        }

        List<Symbol> rows = withinColumnWidths(parsed, source);
        if (rows.isEmpty()) {
            return 0;
        }

        int written;
        if (mode == Mode.INSERT_MISSING) {
            // Counted from the table rather than from the batch's update counts: a JDBC driver is
            // allowed to answer SUCCESS_NO_INFO for a batched statement, which would make the counts
            // a lower bound and the log line a lie.
            long before = symbolRepository.countByCategory(source.category());
            insertMissing(rows);
            written = (int) (symbolRepository.countByCategory(source.category()) - before);
            log.info("{}: {} parsed, {} added, {} already present",
                    source, rows.size(), written, rows.size() - written);
        } else {
            written = upsert(rows);
            log.info("{}: {} parsed, {} upserted", source, rows.size(), written);
        }

        if (source == Source.EQUITY) {
            persistBseAliases(rows, mode);
        }
        return written;
    }

    /**
     * Refresh every reference list, correcting rows that already exist.
     *
     * <p>Deliberately one transaction, as it has always been: a manual refresh is a single
     * administrative act. The pre-start loader calls {@link #load} per source instead, so one dead
     * provider cannot cost it the sources that answered.
     */
    @Transactional
    public void refreshAll() {
        for (Source source : Source.values()) {
            load(source, Mode.UPSERT);
        }
        int fixed = holdingService.backfillMissingIsins();
        log.info("Symbol refresh complete (fixed {} holding ISINs)", fixed);
    }

    public void refreshEquities() {
        load(Source.EQUITY, Mode.UPSERT);
    }

    public void refreshEtfs() {
        load(Source.ETF, Mode.UPSERT);
    }

    public void refreshMutualFunds() {
        load(Source.MUTUAL_FUND, Mode.UPSERT);
    }

    public void refreshBonds() {
        load(Source.BOND, Mode.UPSERT);
    }

    public void refreshNpsSchemes() {
        load(Source.NPS, Mode.UPSERT);
    }

    // ── parsers: one per source, HTTP and format only ─────────────────

    private List<Symbol> parseNseEquities() throws Exception {
        List<Symbol> out = new ArrayList<>();
        forEachCsvRow(NSE_CSV_URL, "text/csv,application/csv", 30_000, cols -> {
            if (cols.length < 3) return;
            String symbol = cols[0].trim();
            String name = unquote(cols[1]);
            // Series EQ only: the rest of EQUITY_L is rights entitlements, partly paid shares and
            // suspended scrips, none of which anybody holds as an equity position.
            if (symbol.isEmpty() || !"EQ".equals(cols[2].trim())) return;
            out.add(Symbol.builder()
                    .symbol(symbol + ".NS")
                    .name(name.isBlank() ? symbol : name)
                    .category(Source.EQUITY.category())
                    .sector("Equity")
                    .isin(isinOrNull(cols.length > 6 ? unquote(cols[6]) : null))
                    .build());
        });
        return out;
    }

    /**
     * Exchange-traded funds, from NSE's own ETF list.
     *
     * <p>These were previously unreachable: {@link #parseNseEquities} filters {@code EQUITY_L.csv} to
     * series {@code EQ}, and ETFs are not in it, so no {@code NIFTYBEES.NS} row could ever exist —
     * and without the row a holding cannot resolve an ISIN or a price. Nothing else about ETF pricing
     * needed changing; the list was the whole gap.
     *
     * <p>{@code Symbol,Underlying,SecurityName,DateofListing,MarketLot,ISINNumber,FaceValue}
     */
    private List<Symbol> parseNseEtfs() throws Exception {
        List<Symbol> out = new ArrayList<>();
        forEachCsvRow(NSE_ETF_CSV_URL, "text/csv,application/csv", 30_000, cols -> {
            if (cols.length < 3) return;
            String symbol = cols[0].trim();
            if (symbol.isEmpty()) return;
            String underlying = unquote(cols[1]);
            String name = unquote(cols[2]);
            out.add(Symbol.builder()
                    .symbol(symbol + ".NS")
                    .name(name.isBlank() ? symbol : name)
                    .category(Source.ETF.category())
                    // The index or metal tracked, which is the only thing that distinguishes two
                    // otherwise identically named ETFs in a dropdown.
                    .sector(underlying.isBlank() ? "ETF" : "ETF | " + underlying)
                    .isin(isinOrNull(cols.length > 5 ? unquote(cols[5]) : null))
                    .build());
        });
        return out;
    }

    private List<Symbol> parseNseBonds() throws Exception {
        List<Symbol> out = new ArrayList<>();
        forEachCsvRow(NSE_DEBT_CSV_URL, "text/csv,application/csv", 30_000, cols -> {
            if (cols.length < 14) return;
            String symbol = cols[0].trim();
            String name = unquote(cols[1]);
            if (symbol.isEmpty() || name.isEmpty()) return;

            String series = cols[2].trim();
            String faceValue = cols[3].trim();
            String ipRate = cols.length > 6 ? cols[6].trim() : "";
            String redemptionDate = cols.length > 9 ? cols[9].trim() : "";

            StringBuilder sector = new StringBuilder("Bond");
            if (!ipRate.isEmpty()) sector.append(" | Coupon: ").append(ipRate).append('%');
            if (!redemptionDate.isEmpty()) sector.append(" | Maturity: ").append(redemptionDate);
            if (!faceValue.isEmpty()) sector.append(" | FV: ₹").append(faceValue);

            out.add(Symbol.builder()
                    .symbol(symbol + ".NS")
                    .name(name + (series.isEmpty() ? "" : " [" + series + "]"))
                    .category(Source.BOND.category())
                    .sector(sector.toString())
                    .isin(isinOrNull(cols.length > 14 ? unquote(cols[14]) : null))
                    .build());
        });
        return out;
    }

    /** AMFI's bulk NAV file, which is semicolon-delimited and UTF-8 rather than an NSE archive CSV. */
    private List<Symbol> parseAmfiFunds() throws Exception {
        HttpURLConnection conn = openConnection(MF_NAV_URL, "text/plain", 60_000);
        if (conn.getResponseCode() != 200) {
            throw new IllegalStateException("AMFI NAV returned status " + conn.getResponseCode());
        }

        List<Symbol> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (!line.contains(";") || line.contains("Scheme Code") || line.endsWith("Fund)")) continue;

                String[] cols = line.split(";");
                if (cols.length < 4) continue;

                // Growth ISIN if there is one, otherwise the reinvestment ISIN.
                String isin = null;
                if (cols.length > 1 && cols[1].trim().length() == ISIN_LENGTH && !cols[1].trim().equals("-")) {
                    isin = cols[1].trim();
                }
                if (isin == null && cols.length > 2 && cols[2].trim().length() == ISIN_LENGTH && !cols[2].trim().equals("-")) {
                    isin = cols[2].trim();
                }
                if (isin == null) continue;

                String schemeName = cols[3].trim();
                if (schemeName.isEmpty()) continue;

                // The ISIN is both the primary key and the isin column: every price and NAV lookup
                // for a fund goes through the ISIN, never through a scheme name.
                out.add(Symbol.builder()
                        .symbol(isin)
                        .name(schemeName)
                        .category(Source.MUTUAL_FUND.category())
                        .sector("Mutual Fund")
                        .schemeCode(cols[0].trim())
                        .isin(isin)
                        .build());
            }
        }
        return out;
    }

    /**
     * NPS pension-fund schemes, keyed by scheme code.
     *
     * <p>These existed only in {@code NpsNavService}'s in-memory cache before this — a field, lost on
     * every restart — so an NPS holding had nothing to validate against and no key to hang day-wise
     * NAV history off. Refreshing repopulates that cache as a side effect, which is wanted.
     */
    private List<Symbol> parseNpsSchemes() {
        npsNavService.refreshSchemes();
        List<Map<String, String>> schemes = npsNavService.getSchemes();
        List<Symbol> out = new ArrayList<>(schemes.size());
        for (Map<String, String> scheme : schemes) {
            String code = scheme.get("schemeCode");
            String name = scheme.get("schemeName");
            if (code == null || code.isBlank() || name == null || name.isBlank()) continue;
            code = code.trim();
            out.add(Symbol.builder()
                    .symbol(code)
                    .name(name.trim())
                    .category(Source.NPS.category())
                    .sector("Pension Fund")
                    // Also in scheme_code, so idx_symbols_scheme_code finds it and a holding that
                    // stored the code in either place resolves.
                    .schemeCode(code)
                    .build());
        }
        return out;
    }

    // ── persistence ───────────────────────────────────────────────────

    private void insertMissing(List<Symbol> rows) {
        batchWrite(INSERT_SYMBOL_SQL, rows);
    }

    /**
     * Correct existing rows and add new ones, in one statement per thousand.
     *
     * <p>This used to be a read-then-write loop over JPA, and that was not merely slow (a SELECT and a
     * save per row, ~44,000 round trips for a full refresh, with no {@code hibernate.jdbc.batch_size}
     * configured to amortise them) — it was wrong. A new NSE listing was written to the persistence
     * context and not flushed until commit, while {@link #persistBseAliases} writes through
     * {@code JdbcTemplate} immediately, so the alias hit {@code symbol_aliases_symbol_fkey} before its
     * symbol existed. Verified live: a manual refresh aborted on {@code BANKA.NS}, a symbol listed
     * since the previous run, taking the whole batch and the refresh step with it. Both writes now go
     * through the same connection in the order they are issued.
     *
     * @return the number of rows written — insert or update, since {@code ON CONFLICT DO UPDATE}
     *         touches every one
     */
    private int upsert(List<Symbol> rows) {
        batchWrite(UPSERT_SYMBOL_SQL, rows);
        return rows.size();
    }

    private void batchWrite(String sql, List<Symbol> rows) {
        Timestamp now = Timestamp.from(Instant.now());
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<Symbol> chunk = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Symbol s = chunk.get(i);
                    ps.setString(1, s.getSymbol());
                    ps.setString(2, s.getName());
                    ps.setString(3, s.getCategory());
                    setNullable(ps, 4, s.getSector());
                    setNullable(ps, 5, s.getIsin());
                    setNullable(ps, 6, s.getSchemeCode());
                    ps.setTimestamp(7, now);
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
    }

    /**
     * The Alpha Vantage ticker for each NSE equity, which is the BSE form of the same symbol.
     *
     * <p>Batched with the symbols rather than looked up per row: this used to cost a SELECT and an
     * INSERT for each of ~2,000 equities on every refresh, to write a value derived deterministically
     * from the key.
     */
    private void persistBseAliases(List<Symbol> equities, Mode mode) {
        String sql = mode == Mode.INSERT_MISSING ? INSERT_ALIAS_SQL : UPSERT_ALIAS_SQL;
        for (int start = 0; start < equities.size(); start += BATCH_SIZE) {
            List<Symbol> chunk = equities.subList(start, Math.min(start + BATCH_SIZE, equities.size()));
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    String symbol = chunk.get(i).getSymbol();
                    ps.setString(1, symbol);
                    ps.setString(2, "ALPHA_VANTAGE");
                    ps.setString(3, symbol.replace(".NS", ".BSE"));
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
    }

    /**
     * Drop what cannot be stored, trim what can be, and say so.
     *
     * <p>This matters more with a batched insert than it did with a row at a time: one oversized value
     * fails the whole batch, taking every valid row in it down as well. Nothing is silently
     * truncated without a count in the log.
     */
    private List<Symbol> withinColumnWidths(List<Symbol> parsed, Source source) {
        List<Symbol> kept = new ArrayList<>(parsed.size());
        List<String> dropped = new ArrayList<>();
        int trimmed = 0;

        for (Symbol s : parsed) {
            if (s.getSymbol() == null || s.getSymbol().isBlank() || s.getSymbol().length() > MAX_SYMBOL) {
                dropped.add(String.valueOf(s.getSymbol()));
                continue;
            }
            if (s.getSchemeCode() != null && s.getSchemeCode().length() > MAX_SCHEME_CODE) {
                dropped.add(s.getSymbol());
                continue;
            }
            if (s.getName() != null && s.getName().length() > MAX_NAME) {
                s.setName(s.getName().substring(0, MAX_NAME));
                trimmed++;
            }
            if (s.getSector() != null && s.getSector().length() > MAX_SECTOR) {
                s.setSector(s.getSector().substring(0, MAX_SECTOR));
                trimmed++;
            }
            kept.add(s);
        }

        if (!dropped.isEmpty()) {
            log.warn("{}: dropped {} row(s) that do not fit the symbols table: {}", source, dropped.size(),
                    dropped.stream().limit(10).collect(Collectors.joining(", ")));
        }
        if (trimmed > 0) {
            log.info("{}: truncated {} over-long name/sector value(s)", source, trimmed);
        }
        return kept;
    }

    // ── reads ─────────────────────────────────────────────────────────

    public List<Map<String, String>> getAllSymbols() {
        return symbolRepository.findAll().stream()
                .map(this::symbolToMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, String>> getSymbolsByCategory(String category) {
        return symbolRepository.findByCategory(category.toUpperCase()).stream()
                .map(this::symbolToMap)
                .collect(Collectors.toList());
    }

    private Map<String, String> symbolToMap(Symbol s) {
        var map = new java.util.HashMap<String, String>();
        map.put("symbol", s.getSymbol());
        map.put("name", s.getName());
        map.put("category", s.getCategory());
        map.put("sector", s.getSector() != null ? s.getSector() : "");
        map.put("isin", s.getIsin() != null ? s.getIsin() : "");
        return map;
    }

    // ── plumbing ──────────────────────────────────────────────────────

    /**
     * Stream one NSE archive CSV, handing each parsed data row to {@code row}.
     *
     * @throws IllegalStateException on any status but 200, so {@link #load} reports the source as
     *         failed rather than as empty — an empty list and a 403 need different messages
     */
    private void forEachCsvRow(String url, String accept, int readTimeout, Consumer<String[]> row) throws Exception {
        HttpURLConnection conn = openConnection(url, accept, readTimeout);
        if (conn.getResponseCode() != 200) {
            throw new IllegalStateException(url + " returned status " + conn.getResponseCode());
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), NSE_CHARSET))) {
            if (br.readLine() == null) return;   // header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                row.accept(parseCsvLine(line));
            }
        }
    }

    private HttpURLConnection openConnection(String url, String accept, int readTimeout) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Accept", accept);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(readTimeout);
        return conn;
    }

    private static void setNullable(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static String unquote(String field) {
        return field == null ? "" : field.trim().replaceAll("^\"|\"$", "");
    }

    /** An ISIN is exactly 12 characters; anything else is a placeholder such as {@code -}. */
    private static String isinOrNull(String isin) {
        if (isin == null) return null;
        String trimmed = isin.trim();
        return trimmed.length() == ISIN_LENGTH ? trimmed : null;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') { inQuotes = !inQuotes; continue; }
            if (c == ',' && !inQuotes) { fields.add(sb.toString()); sb = new StringBuilder(); continue; }
            sb.append(c);
        }
        fields.add(sb.toString());
        return fields.toArray(String[]::new);
    }
}
