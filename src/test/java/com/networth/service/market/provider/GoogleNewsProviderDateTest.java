package com.networth.service.market.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An article's publication time is an instant, and RSS feeds say so with an offset.
 *
 * <p>The offset used to be discarded, which mattered because feeds disagree about zones: a story
 * published "10:30 +0530" is two hours <em>older</em> than one published "10:00 GMT", but compared
 * on wall-clock alone it looks half an hour newer. News is presented newest-first, so dropping the
 * offset silently reordered the feed.
 */
class GoogleNewsProviderDateTest {

    /** Two items whose wall-clock order is the reverse of their true chronological order. */
    private static final String MIXED_ZONE_RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
              <item>
                <title>Published 10:30 IST</title>
                <link>https://example.com/ist</link>
                <source>IST Feed</source>
                <pubDate>Sun, 09 Aug 2026 10:30:00 +0530</pubDate>
                <description>earlier in real time</description>
              </item>
              <item>
                <title>Published 10:00 GMT</title>
                <link>https://example.com/gmt</link>
                <source>GMT Feed</source>
                <pubDate>Sun, 09 Aug 2026 10:00:00 GMT</pubDate>
                <description>later in real time</description>
              </item>
            </channel></rss>
            """;

    private GoogleNewsProvider providerReturning(String xml) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(xml);
        return new GoogleNewsProvider(restTemplate);
    }

    @Test
    @DisplayName("an offset in the feed is applied, not dropped")
    void offsetIsApplied() {
        List<NewsItem> items = providerReturning(MIXED_ZONE_RSS).fetchNews("RELIANCE", 10);

        assertThat(items).hasSize(2);
        // 10:30 +05:30 is 05:00Z, not 10:30Z.
        assertThat(items.get(0).getPubDate()).isEqualTo(Instant.parse("2026-08-09T05:00:00Z"));
        assertThat(items.get(1).getPubDate()).isEqualTo(Instant.parse("2026-08-09T10:00:00Z"));
    }

    @Test
    @DisplayName("newest-first ordering follows real time, not wall-clock time")
    void newestFirstUsesRealTime() {
        List<NewsItem> items = providerReturning(MIXED_ZONE_RSS).fetchNews("RELIANCE", 10);

        List<String> newestFirst = items.stream()
                .sorted(Comparator.comparing(NewsItem::getPubDate).reversed())
                .map(NewsItem::getTitle)
                .toList();

        // On wall-clock alone the IST story (10:30) would sort first. It is actually the older one.
        assertThat(newestFirst).containsExactly("Published 10:00 GMT", "Published 10:30 IST");
    }

    @Test
    @DisplayName("a wrong day name does not cost the article its date")
    void wrongDayNameStillKeepsTheOffsetAndDate() {
        // 09 Aug 2026 was a Sunday. Strict RFC-1123 rejects "Sat", and the old fallback only
        // understood named zones, so a feed with both quirks had every article stamped "now".
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                  <item>
                    <title>Wrong weekday</title><link>https://example.com/w</link>
                    <pubDate>Sat, 09 Aug 2026 10:30:00 +0530</pubDate>
                  </item>
                </channel></rss>
                """;
        List<NewsItem> items = providerReturning(rss).fetchNews("RELIANCE", 10);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPubDate()).isEqualTo(Instant.parse("2026-08-09T05:00:00Z"));
    }

    @Test
    @DisplayName("a header with no day name at all is still dated")
    void noDayNameIsStillParsed() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                  <item>
                    <title>No day name</title><link>https://example.com/n</link>
                    <pubDate>09 Aug 2026 10:30:00 +0530</pubDate>
                  </item>
                </channel></rss>
                """;
        List<NewsItem> items = providerReturning(rss).fetchNews("RELIANCE", 10);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPubDate()).isEqualTo(Instant.parse("2026-08-09T05:00:00Z"));
    }

    @Test
    @DisplayName("an unparseable or absent date falls back to now rather than dropping the item")
    void unparseableDateFallsBackToNow() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                  <item>
                    <title>No date</title><link>https://example.com/x</link>
                    <pubDate>not a date at all</pubDate>
                  </item>
                </channel></rss>
                """;
        Instant before = Instant.now();
        List<NewsItem> items = providerReturning(rss).fetchNews("RELIANCE", 10);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPubDate()).isBetween(before, Instant.now());
    }
}
