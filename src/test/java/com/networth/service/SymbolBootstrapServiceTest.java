package com.networth.service;

import com.networth.repository.SymbolRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * When the pre-start loader blocks, when it defers, and when it does nothing at all.
 *
 * <p>The decision matters more than it looks. Strict ticker validation is only safe if the lists behind
 * it are populated, and nothing used to populate them: {@code refreshAll()} was reachable only from a
 * controller and a manual job, so a fresh deployment came up with an empty {@code symbols} table and
 * would have refused every holding a user tried to create. Blocking on an empty category is the fix.
 * Blocking on a merely stale one would be a regression — 90 seconds of provider calls added to every
 * restart to learn about instruments listed since last week.
 *
 * <p>The recording executor is what makes "deferred, not blocking" observable: it captures the task
 * without running it, so a submitted refresh and an inline one are distinguishable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SymbolBootstrapServiceTest {

    @Mock SymbolService symbolService;
    @Mock SymbolRepository symbolRepository;

    private final List<Runnable> submitted = new ArrayList<>();
    private final Executor recordingExecutor = submitted::add;

    private SymbolBootstrapService service(boolean enabled) {
        return new SymbolBootstrapService(symbolService, symbolRepository, recordingExecutor,
                enabled, Duration.ofDays(7));
    }

    /** Every category present and refreshed an hour ago — the warm-boot baseline. */
    private void everythingFresh() {
        when(symbolRepository.countByCategory(anyString())).thenReturn(2_000L);
        when(symbolRepository.findMaxUpdatedAtByCategory(anyString()))
                .thenReturn(Optional.of(Instant.now().minus(1, ChronoUnit.HOURS)));
    }

    @Test
    @DisplayName("an empty category is loaded synchronously, before the app is ready to serve")
    void anEmptyCategoryBlocks() {
        everythingFresh();
        when(symbolRepository.countByCategory("NPS")).thenReturn(0L);

        service(true).load();

        // Loaded inline, not handed to the executor: a request that arrives before this finishes would
        // be told SM001001 does not exist.
        verify(symbolService).load(SymbolService.Source.NPS, SymbolService.Mode.INSERT_MISSING);
        assertThat(submitted).as("an empty category must not be deferred").isEmpty();
        // And the four populated ones are left alone.
        verify(symbolService, times(1)).load(any(), any());
    }

    @Test
    @DisplayName("a populated, fresh list is left alone")
    void freshListsAreNotTouched() {
        everythingFresh();

        service(true).load();

        verify(symbolService, never()).load(any(), any());
        assertThat(submitted).isEmpty();
    }

    @Test
    @DisplayName("a stale list refreshes in the background, without delaying readiness")
    void staleListsAreDeferred() {
        everythingFresh();
        when(symbolRepository.countByCategory("EQUITY")).thenReturn(2_075L);
        when(symbolRepository.findMaxUpdatedAtByCategory("EQUITY"))
                .thenReturn(Optional.of(Instant.now().minus(30, ChronoUnit.DAYS)));

        service(true).load();

        // Nothing ran yet — that is the assertion. A month-old equity list is missing a handful of new
        // listings, not 2,075 symbols, so it is not worth a boot delay.
        verify(symbolService, never()).load(any(), any());
        assertThat(submitted).hasSize(1);

        submitted.get(0).run();   // the background thread, on this one
        verify(symbolService).load(SymbolService.Source.EQUITY, SymbolService.Mode.INSERT_MISSING);
    }

    @Test
    @DisplayName("a list with rows but no updated_at is treated as stale, not as fresh")
    void anUnknownAgeCountsAsStale() {
        when(symbolRepository.countByCategory(anyString())).thenReturn(500L);
        when(symbolRepository.findMaxUpdatedAtByCategory(anyString())).thenReturn(Optional.empty());

        service(true).load();

        assertThat(submitted).as("one deferred refresh per source")
                .hasSize(SymbolService.Source.values().length);
    }

    @Test
    @DisplayName("a source that throws does not stop the boot")
    void aFailingSourceDoesNotPropagate() {
        when(symbolRepository.countByCategory(anyString())).thenReturn(0L);
        doThrow(new RuntimeException("NSE returned 403"))
                .when(symbolService).load(eq(SymbolService.Source.EQUITY), any());

        // A dead NSE archive and a timed-out AMFI have both been observed against this codebase. Either
        // must surface later as a rejected ticker naming the empty list, which is diagnosable — not as a
        // boot loop, which is not.
        assertThatCode(() -> service(true).load()).doesNotThrowAnyException();

        // And the other four are still attempted.
        verify(symbolService, times(SymbolService.Source.values().length)).load(any(), any());
    }

    @Test
    @DisplayName("the switch is honoured: disabled means nothing is read or loaded")
    void disabledDoesNothing() {
        service(false).onApplicationStarted();

        verifyNoInteractions(symbolService, symbolRepository);
        assertThat(submitted).isEmpty();
    }
}
