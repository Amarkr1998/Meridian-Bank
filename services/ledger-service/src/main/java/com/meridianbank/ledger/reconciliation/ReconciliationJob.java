package com.meridianbank.ledger.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The scheduled entry point for {@link ReconciliationService#run()} — see
 * docs/architecture/reconciliation-flow.md's "Scheduled Job" actor. Deliberately thin: all real
 * logic (and all of it that tests exercise directly) lives in {@link ReconciliationService}, which
 * has no dependency on Spring's scheduler and can be called synchronously in a test without
 * waiting on a timer.
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final ReconciliationService reconciliationService;

    public ReconciliationJob(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelayString = "${meridian.reconciliation.poll-interval-ms:60000}")
    public void run() {
        try {
            reconciliationService.run();
        } catch (Exception e) {
            // A single bad run must not deregister the schedule (an uncaught exception from a
            // @Scheduled method would) — log and let the next fixed-delay tick try again.
            log.error("Reconciliation run failed, will retry on the next scheduled tick", e);
        }
    }
}
