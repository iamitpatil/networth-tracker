package com.networth.service.market.provider;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What each market data provider allows, and what we allow ourselves.
 *
 * <p>Every limit here is recorded with the figure the provider publishes <em>and</em> the budget
 * this application enforces, because the two are deliberately different. Sitting exactly on a
 * published limit gets you 429s: clocks drift, retries overlap, and a burst that is legal over a
 * minute can be illegal over any given second inside it. The enforced budget is roughly 80% of
 * documented, and well under it for providers that publish no figure at all.
 *
 * <p>The ones that matter, and why:
 *
 * <ul>
 *   <li><b>Alpha Vantage</b> is the dangerous one. The free tier is <b>25 requests per day</b>, and
 *       it sits last in the price chain — so it is only reached once Upstox <em>and</em> Yahoo have
 *       both failed, which is exactly when a retry storm would burn the day's entire quota in
 *       seconds. Their own pages disagree: the pricing page says 25/day, the support page says
 *       25/minute. The conservative reading is used.</li>
 *   <li><b>Upstox</b> publishes 50/s, 500/min and 2000/30min, per API per user. The 30-minute
 *       ceiling is the one that actually binds a backfill: 400/min sustained would be 12,000 in
 *       half an hour, six times the cap. Bursts are allowed; sustained throughput is not.</li>
 *   <li><b>Yahoo, NSE, gold and news</b> publish nothing. Yahoo's chart endpoint is an unofficial
 *       API and NSE actively discourages scripted access, so these get deliberately small budgets
 *       — being throttled by us is cheaper than being IP-banned by them.</li>
 *   <li><b>npsnav.in</b> publishes nothing either, and is a free community API. Its budget is sized
 *       from the job that uses it rather than from a published figure: the NPS history load is one
 *       request per scheme for 282 schemes, and 1/s walks through that in about five minutes.</li>
 *   <li><b>AMFI</b> is not per-symbol at all: one request downloads every NAV in the country as a
 *       text file. A handful of fetches a day is the right volume; hammering it would be both rude
 *       and pointless.</li>
 * </ul>
 *
 * <p>Override with {@code market.ratelimit.providers.<name>.per-minute=...} and so on. Keys not
 * mentioned in configuration keep the defaults below, so tuning one provider cannot silently
 * unlimit the rest.
 */
@Configuration
@ConfigurationProperties(prefix = "market.ratelimit")
@Getter
@Setter
public class ProviderRateLimits {

    /** Master switch. Off means every request is allowed, which is the pre-existing behaviour. */
    private boolean enabled = true;

    /**
     * How long a caller that can afford to wait (the backfill) will block for a slot before giving
     * up. The live read path never waits — it falls through to the next provider instead.
     */
    private Duration maxWait = Duration.ofSeconds(2);

    private Map<String, Limit> providers = defaults();

    /** The budget for one provider. A null window is simply not enforced. */
    @Getter
    @Setter
    public static class Limit {
        private Integer perSecond;
        private Integer perMinute;
        private Integer per30Minutes;
        private Integer perDay;

        /** The provider's published limit, verbatim, for display. */
        private String documented;

        /** Where {@link #documented} came from, or a note that nothing is published. */
        private String source;

        Limit(Integer perSecond, Integer perMinute, Integer per30Minutes, Integer perDay,
              String documented, String source) {
            this.perSecond = perSecond;
            this.perMinute = perMinute;
            this.per30Minutes = per30Minutes;
            this.perDay = perDay;
            this.documented = documented;
            this.source = source;
        }

        /** Required for configuration binding. */
        public Limit() {
        }

        /** The enforced windows. Only non-null ones are enforced. */
        public List<Window> windows() {
            List<Window> windows = new ArrayList<>(4);
            if (perSecond != null) windows.add(new Window(perSecond, Duration.ofSeconds(1)));
            if (perMinute != null) windows.add(new Window(perMinute, Duration.ofMinutes(1)));
            if (per30Minutes != null) windows.add(new Window(per30Minutes, Duration.ofMinutes(30)));
            if (perDay != null) windows.add(new Window(perDay, Duration.ofDays(1)));
            return windows;
        }

        /** The longest window tracked, which bounds how long timestamps must be kept. */
        public Duration longestWindow() {
            return windows().stream().map(Window::window).max(Duration::compareTo).orElse(Duration.ZERO);
        }
    }

    /** A count allowed within a rolling period. */
    public record Window(int limit, Duration window) {

        /** "40/s", "400/min", "2000/30min", "25/day" — how the limit reads to a person. */
        public String label() {
            long seconds = window.getSeconds();
            if (seconds == 1) return limit + "/s";
            if (seconds == 60) return limit + "/min";
            if (seconds == 86400) return limit + "/day";
            return limit + "/" + (seconds / 60) + "min";
        }
    }

    public Limit forProvider(String name) {
        return providers.get(name);
    }

    private static Map<String, Limit> defaults() {
        Map<String, Limit> map = new LinkedHashMap<>();

        String upstoxDocs = "https://upstox.com/developer/api-documentation/rate-limiting/";
        String upstoxPublished = "50/s, 500/min, 2000/30min per API per user";

        // 80% of the published per-second and per-minute figures, and the full 30-minute ceiling
        // because that one is a hard cap rather than a burst allowance.
        map.put("upstox", new Limit(40, 400, 2000, null, upstoxPublished, upstoxDocs));
        map.put("upstox-mf", new Limit(40, 400, 2000, null, upstoxPublished, upstoxDocs));

        // A separate bucket from the quote API on purpose: Upstox counts per API, and the
        // historical-candle endpoint is where a backfill spends its whole budget.
        map.put("upstox-historical", new Limit(20, 200, 1800, null, upstoxPublished, upstoxDocs));

        map.put("yahoo", new Limit(5, 60, null, null,
                "not published; the chart endpoint is an unofficial API",
                "undocumented — self-imposed to avoid an IP block"));

        map.put("alpha-vantage", new Limit(1, 5, null, 25,
                "25 requests/day on the free tier",
                "https://www.alphavantage.co/premium/ — their support page says 25/min; "
                        + "the lower figure is assumed"));

        map.put("amfi", new Limit(null, 2, null, 24,
                "not published; one request returns every NAV as a bulk text file",
                "undocumented — a bulk file does not need frequent fetching"));

        map.put("nse", new Limit(1, 10, null, null,
                "not published; scripted access is actively discouraged",
                "undocumented — deliberately small"));

        map.put("gold", new Limit(1, 10, null, null, "not published", "undocumented"));

        // One request returns a whole scheme's NAV history, and the full historical load is 282 of
        // them. 1/s sustained paces that at about five minutes, which is unhurried for a background
        // job and gentle on a free API that publishes no limit at all. The per-minute figure is set
        // to exactly 60 so it never binds tighter than the per-second one — a lower ceiling would
        // stall the backfill in bursts rather than pace it evenly.
        map.put("npsnav", new Limit(1, 60, null, null,
                "not published; a free community API",
                "undocumented — 1/s paces the 282-scheme historical load at about five minutes"));

        map.put("google", new Limit(1, 20, null, null, "not published (news RSS)", "undocumented"));

        return map;
    }
}
