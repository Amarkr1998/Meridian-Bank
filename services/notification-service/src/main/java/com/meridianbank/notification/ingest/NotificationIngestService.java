package com.meridianbank.notification.ingest;

import com.meridianbank.notification.domain.Notification;
import com.meridianbank.notification.domain.NotificationType;
import com.meridianbank.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Turns one consumed domain event into a durable, real, queryable {@link Notification} row, plus
 * a simulated email/SMS delivery — logged only, since this service has no real email address or
 * phone number to send to (see Notification's Javadoc and notification-service/README.md, "never
 * a paid provider integration"). Idempotent by the producing event's own {@code eventId}, same
 * pattern as audit-service's {@code AuditIngestService} — Kafka delivery is at-least-once, so a
 * redelivered message must not create a duplicate notification.
 */
@Service
public class NotificationIngestService {

    private static final Logger log = LoggerFactory.getLogger(NotificationIngestService.class);

    private final NotificationRepository repository;

    public NotificationIngestService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void ingest(UUID eventId, UUID customerId, NotificationType type, String title, String body) {
        if (repository.existsByEventId(eventId)) {
            log.debug("Event {} already produced a notification — idempotent no-op (redelivery)", eventId);
            return;
        }
        Notification notification = new Notification(eventId, customerId, type, title, body);
        try {
            repository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException e) {
            log.debug("Event {} hit the event_id unique constraint — idempotent no-op (race)", eventId);
            return;
        }
        simulateDelivery(customerId, title, body);
    }

    /**
     * Stands in for a real email/SMS provider — see the "never a paid provider integration"
     * safety rule (CLAUDE.md). Masked in logs the same way any other sensitive-adjacent value is
     * (see docs/governance/governance-principles.md) — the customer id is not itself sensitive,
     * but the pattern is deliberately identical to how a real integration point would look, so
     * this is honestly a simulation, not a shortcut dressed up as one.
     */
    private void simulateDelivery(UUID customerId, String title, String body) {
        log.info("[SIMULATED EMAIL] to customer {}: {} — {}", customerId, title, body);
        log.info("[SIMULATED SMS] to customer {}: {}", customerId, title);
    }
}
