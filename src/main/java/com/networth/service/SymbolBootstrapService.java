package com.networth.service;

import com.networth.repository.SymbolRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Loads the ticker reference lists before the application accepts traffic.
 *
 * <p>This exists because rejecting unknown tickers is only safe if the lists behind the rejection are
 * populated. Nothing used to populate them at startup: {@code refreshAll()} was reachable only from
 * {@code SymbolController} and {@code BackfillJobService}, both manual, so a fresh deployment came up
 * with an empty {@code symbols} table — and with strict validation in place, that deployment would
 * refuse every holding a user tried to create.
 *
 * <p>Per category the policy is:
 *
 * <ul>
 *   <li><b>empty</b> — load it now, blocking, because no write to that asset class can be answered
 *       correctly without it
 *   <li><b>present but older than {@code app.symbols.max-age}</b> — refresh on a background thread and
 *       let boot continue; a list a week out of date is missing a handful of new listings, not every
 *       symbol
 *   <li><b>present and fresh</b> — do nothing
 * </ul>
 *
 * <p>So a cold boot costs one full load (~60–90s of provider calls) and a warm boot costs five indexed
 * {@code count(*)} queries.
 *
 * <p>Kept apart from {@link SymbolService} on purpose: that class knows how to parse each provider's
 * format, this one knows when it is worth asking. Neither needs to change when the other does.
 */
@Slf4j
@Service
public class SymbolBootstrapService {

    private final SymbolService symbolService;
    private final SymbolRepository symbolRepository;
    private final Executor asyncExecutor;

    private final boolean enabled;
    private final Duration maxAge;

    public SymbolBootstrapService(SymbolService symbolService,
                                  SymbolRepository symbolRepository,
                                  Executor asyncExecutor,
                                  @Value("${app.symbols.bootstrap-enabled:true}") boolean enabled,
                                  @Value("${app.symbols.max-age:7d}") Duration maxAge) {
        this.symbolService = symbolService;
        this.symbolRepository = symbolRepository;
        this.asyncExecutor = asyncExecutor;
        this.enabled = enabled;
        this.maxAge = maxAge;
    }

    /**
     * Run the load while the application is starting, before it is marked ready.
     *
     * <p>{@link ApplicationStartedEvent}, not {@code ApplicationReadyEvent}, and the difference is the
     * whole point. {@code ApplicationReadyEvent} fires <em>after</em> the connector begins accepting
     * requests — which is right for {@code StartupBackfillService}, whose work is optional, but wrong
     * here: it would leave a window in which the app is serving and strict validation rejects
     * perfectly valid tickers because the list behind it is still empty. Blocking in this listener
     * delays readiness instead, which is the trade we want.
     */
    @EventListener(ApplicationStartedEvent.class)
    public void onApplicationStarted() {
        if (!enabled) {
            log.info("Symbol bootstrap disabled (app.symbols.bootstrap-enabled=false)");
            return;
        }
        load();
    }

    /** The policy itself, separate from the event so a test can call it without publishing one. */
    void load() {
        Instant startedAt = Instant.now();
        List<String> loaded = new ArrayList<>();
        List<String> queued = new ArrayList<>();
        List<String> alreadyFresh = new ArrayList<>();

        for (SymbolService.Source source : SymbolService.Source.values()) {
            long count = symbolRepository.countByCategory(source.category());

            if (count == 0) {
                loaded.add(source.name());
                loadQuietly(source);
                continue;
            }

            Instant lastUpdated = symbolRepository.findMaxUpdatedAtByCategory(source.category()).orElse(null);
            boolean stale = lastUpdated == null
                    || Duration.between(lastUpdated, Instant.now()).compareTo(maxAge) > 0;

            if (stale) {
                queued.add(source.name());
                // Deliberately not blocking: readiness should not wait on a provider to tell us about
                // instruments listed since last week.
                asyncExecutor.execute(() -> loadQuietly(source));
            } else {
                alreadyFresh.add(source.name());
            }
        }

        log.info("Symbol bootstrap finished in {}ms — loaded {}, refreshing {} in the background, {} already fresh",
                Duration.between(startedAt, Instant.now()).toMillis(), loaded, queued, alreadyFresh);
    }

    /**
     * Load one source, absorbing any failure.
     *
     * <p>A dead NSE archive or a timed-out AMFI — both already observed against this codebase — must
     * not stop the application from booting. The consequence surfaces later as a rejected ticker whose
     * message names the empty list, which is diagnosable; a boot loop is not.
     */
    private void loadQuietly(SymbolService.Source source) {
        try {
            symbolService.load(source, SymbolService.Mode.INSERT_MISSING);
        } catch (Exception e) {
            log.error("Symbol bootstrap could not load {}: {}", source, e.getMessage());
        }
    }
}
