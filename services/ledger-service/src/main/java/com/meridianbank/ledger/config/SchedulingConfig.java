package com.meridianbank.ledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Backs {@code @Scheduled} (OutboxPublisher and, as of Phase 12, ReconciliationJob). Spring's
 * default fallback scheduler uses non-daemon threads, which can block the JVM from exiting
 * cleanly (noticeable as a hang at test or container shutdown) — daemon threads here fix that
 * without changing polling behavior. Pool size 2 (not 1, unlike the other outbox-only services)
 * so a slow reconciliation run never delays the outbox publisher's own poll cycle, and vice versa.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ledger-scheduled-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
}
