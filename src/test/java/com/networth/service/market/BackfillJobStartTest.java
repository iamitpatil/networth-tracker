package com.networth.service.market;

import com.networth.service.SymbolService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * How a backfill is started, and what the single-job lock actually guarantees.
 *
 * <p>Written after finding that {@code POST /market/backfill} blocked the HTTP worker thread for the
 * entire job — 3.4s against an empty dev database, minutes for a real portfolio's year of
 * rate-limited history. The job body carried {@code @Async}, but it was invoked as {@code this
 * .startFullBackfill(...)} from a sibling method, and a self-invocation never reaches the proxy that
 * implements the annotation. The endpoint answered {@code "started"} only once the work was over,
 * which left the {@code /backfill/status} endpoint the UI polls with nothing left to report.
 *
 * <p>The executor here records instead of running, which is what makes these assertions possible: it
 * freezes the window between claiming the slot and doing the work — a window that did not exist when
 * the job ran inline, and the reason a second caller used to get {@code 200 started} rather than
 * {@code 409}.
 */
@ExtendWith(MockitoExtension.class)
class BackfillJobStartTest {

    @Mock UpstoxHistoricalService upstoxHistoricalService;
    @Mock AmfiHistoricalService amfiHistoricalService;
    @Mock NpsNavService npsNavService;
    @Mock NpsHistoricalService npsHistoricalService;
    @Mock SymbolEventService symbolEventService;
    @Mock SymbolService symbolService;

    private final List<Runnable> submitted = new ArrayList<>();
    private final Executor recordingExecutor = submitted::add;

    private BackfillJobService service() {
        return new BackfillJobService(upstoxHistoricalService, amfiHistoricalService, npsNavService,
                npsHistoricalService, symbolEventService, symbolService, recordingExecutor);
    }

    @Test
    @DisplayName("the work is handed to the executor, not run on the caller's thread")
    void theWorkIsSubmittedNotRunInline() {
        BackfillJobService service = service();

        assertThat(service.tryStart(UUID.randomUUID(), 365)).isTrue();

        assertThat(submitted).as("exactly one task handed to the background executor").hasSize(1);
        // Nothing has touched a provider: tryStart returned before any step ran, which is the whole
        // point. If @Async were silently not firing again, these would already have been called.
        verifyNoInteractions(symbolService, upstoxHistoricalService, amfiHistoricalService, npsNavService,
                npsHistoricalService, symbolEventService);
    }

    @Test
    @DisplayName("a second caller is refused while the first job is outstanding")
    void aSecondStartIsRefused() {
        BackfillJobService service = service();

        assertThat(service.tryStart(UUID.randomUUID(), 365)).isTrue();
        assertThat(service.tryStart(UUID.randomUUID(), 365))
                .as("the slot is claimed, so this must be refused — the controller turns it into 409")
                .isFalse();
        assertThat(submitted).as("the refused call must not queue a second job").hasSize(1);
    }

    @Test
    @DisplayName("status describes this run before the caller gets its answer")
    void statusIsPublishedBeforeReturning() {
        BackfillJobService service = service();
        UUID userId = UUID.randomUUID();

        service.tryStart(userId, 365);

        // The UI polls /backfill/status as soon as the POST returns. Populating the map inside the
        // background task would let that first poll read the previous run's leftovers, including
        // running=false, and the progress bar would never appear.
        assertThat(service.getStatus())
                .containsEntry("running", true)
                .containsEntry("startedBy", userId.toString())
                .containsEntry("currentStep", "symbols")
                .containsKey("startedAt");
    }

    @Test
    @DisplayName("once the job finishes, the slot is free again")
    void theSlotIsReleasedAfterTheJobRuns() {
        BackfillJobService service = service();
        service.tryStart(UUID.randomUUID(), 365);

        submitted.get(0).run();   // the background thread, on this one

        assertThat(service.isRunning()).isFalse();
        assertThat(service.tryStart(UUID.randomUUID(), 365))
                .as("a completed job must not wedge the lock shut").isTrue();
    }
}
