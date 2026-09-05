package com.meridianbank.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Backs {@code @Scheduled} (OutboxPublisher). Spring's default fallback scheduler uses
 * non-daemon threads, which can block the JVM from exiting cleanly (noticeable as a hang at test
 * or container shutdown) — daemon threads here fix that without changing polling behavior.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("outbox-publisher-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
}
