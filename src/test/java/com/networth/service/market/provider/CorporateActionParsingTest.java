package com.networth.service.market.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Reading a ratio out of NSE's subject line.
 *
 * <p>The subject is prose, and it is the only place the ratio appears. Getting it wrong is not a display
 * problem: the number becomes {@code adjustment_factor} on a split, which scales every earlier lot's
 * per-share cost, so a factor that is out by a multiple silently misstates capital gains for that symbol
 * from then on. Anything unrecognised must therefore produce nothing rather than a guess.
 *
 * <p>Every subject below is a real string returned by
 * {@code /api/corporates-corporateActions} during this work.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CorporateActionParsingTest {

    @Mock RestTemplate restTemplate;
    private NseProvider provider;

    @BeforeEach
    void setUp() {
        provider = new NseProvider(restTemplate);
        // The session warm-up is a separate GET returning HTML; it may fail harmlessly.
        when(restTemplate.exchange(eq("https://www.nseindia.com"), any(HttpMethod.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("<html/>"));
    }

    private void nseReturns(String... subjects) {
        List<Map<String, Object>> rows = java.util.Arrays.stream(subjects)
                .map(s -> Map.of("subject", (Object) s, "exDate", "28-Oct-2024",
                        "recDate", "30-Oct-2024", "isin", "INE002A01018"))
                .toList();
        when(restTemplate.exchange(contains("corporates-corporateActions"), any(HttpMethod.class), any(),
                eq(List.class))).thenReturn(ResponseEntity.ok(rows));
    }

    private CorporateActionEvent only(String subject) {
        nseReturns(subject);
        List<CorporateActionEvent> events = provider.fetchCorporateActions("RELIANCE.NS");
        assertThat(events).as("subject %s should classify", subject).hasSize(1);
        return events.get(0);
    }

    @Test
    @DisplayName("a 1:1 bonus doubles the share count")
    void bonusOneForOne() {
        CorporateActionEvent e = only("Bonus 1:1");
        assertThat(e.getEventType()).isEqualTo("BONUS");
        // NSE writes new:held, so one new per one held leaves twice as many.
        assertThat(e.getRatio()).isEqualByComparingTo("2");
        assertThat(e.getAmountPerShare()).as("a bonus has no per-share payout").isNull();
    }

    @Test
    @DisplayName("a 1:2 bonus multiplies by 1.5, not by 0.5")
    void bonusOneForTwo() {
        // GAIL: 287 held, 143 credited. The multiplier is 1.5, and reading the ratio as new/held instead
        // would give 0.5 and halve the position.
        assertThat(only("Bonus 1:2").getRatio()).isEqualByComparingTo("1.5");
    }

    @Test
    @DisplayName("a 4:1 bonus multiplies by 5")
    void bonusFourForOne() {
        assertThat(only("Bonus Issue 4:1").getRatio()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("a face-value split is read from the face values, not from a ratio")
    void splitFromFaceValues() {
        CorporateActionEvent e = only("Face Value Split From Rs 2 To Re 1");
        assertThat(e.getEventType()).isEqualTo("SPLIT");
        // Rs 2 becomes Re 1, so each share becomes two.
        assertThat(e.getRatio()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("a split from Rs 10 to Rs 2 multiplies by five")
    void splitTenToTwo() {
        assertThat(only("Face Value Split (Sub-Division) - From Rs 10 Per Share To Rs 2 Per Share")
                .getRatio()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("a dividend keeps its amount and its interim or special label")
    void dividendsStillParse() {
        CorporateActionEvent interim = only("Interim Dividend - Rs 6.50 Per Share");
        assertThat(interim.getEventType()).isEqualTo("DIVIDEND");
        assertThat(interim.getEventSubtype()).isEqualTo("Interim");
        assertThat(interim.getAmountPerShare()).isEqualByComparingTo("6.50");
        assertThat(interim.getRatio()).as("a dividend has no ratio").isNull();

        assertThat(only("Special Dividend - Rs 5 Per Share").getEventSubtype()).isEqualTo("Special");
        assertThat(only("Dividend - Rs 17.35 Per Share").getEventSubtype()).isEqualTo("Final");
    }

    @Test
    @DisplayName("a demerger is recognised but carries no ratio of its own")
    void demergerHasNoRatio() {
        CorporateActionEvent e = only("Demerger");
        assertThat(e.getEventType()).isEqualTo("DEMERGER");
        // Its entitlement relates two different companies, so the multiplier belongs to that pairing
        // rather than to this row -- which is why the reclassifier is configured with the parent.
        assertThat(e.getRatio()).isNull();
    }

    @Test
    @DisplayName("an unrecognised subject is dropped rather than guessed at")
    void unknownSubjectsAreDropped() {
        nseReturns("Annual General Meeting", "Change in Registered Office", "");

        assertThat(provider.fetchCorporateActions("RELIANCE.NS"))
                .as("a wrong ratio is worse than a missing event").isEmpty();
    }

    @Test
    @DisplayName("a bonus mentioning rupees is still a bonus, not a dividend")
    void bonusWinsOverAnAmount() {
        // Order matters: extractAmount would happily find "10" here and call it a dividend of Rs 10.
        CorporateActionEvent e = only("Bonus 1:1 (Face Value Rs 10)");
        assertThat(e.getEventType()).isEqualTo("BONUS");
        assertThat(e.getAmountPerShare()).isNull();
    }

    @Test
    @DisplayName("a failed request is null, while a genuinely empty response is an empty list")
    void failureAndEmptinessDiffer() {
        // The event sync depends on this: null means retry, empty means this company has announced nothing
        // and the symbol can be marked done. Collapsing them lets an outage look like "pays nothing".
        when(restTemplate.exchange(contains("corporates-corporateActions"), any(HttpMethod.class), any(),
                eq(List.class))).thenThrow(new RuntimeException("connection reset"));
        assertThat(provider.fetchCorporateActions("RELIANCE.NS")).isNull();

        when(restTemplate.exchange(contains("corporates-corporateActions"), any(HttpMethod.class), any(),
                eq(List.class))).thenReturn(ResponseEntity.ok(List.of()));
        assertThat(provider.fetchCorporateActions("RELIANCE.NS")).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("fetchDividends returns only the dividends from the same single request")
    void dividendsAreFilteredFromTheOneCall() {
        nseReturns("Bonus 1:1", "Dividend - Rs 8 Per Share", "Face Value Split From Rs 2 To Re 1");

        // One request now serves both paths, so the dividend chain cannot disagree with the event store,
        // and a second pass for bonuses would have doubled a four-hour sync at ten requests a minute.
        assertThat(provider.fetchDividends("RELIANCE.NS")).hasSize(1);
        assertThat(provider.fetchCorporateActions("RELIANCE.NS")).hasSize(3);
    }
}
