package com.meridianbank.fraud.service;

import com.meridianbank.fraud.domain.AmlAlert;
import com.meridianbank.fraud.domain.AmlAlertStatus;
import com.meridianbank.fraud.exception.AmlAlertNotFoundException;
import com.meridianbank.fraud.exception.InvalidAlertTransitionException;
import com.meridianbank.fraud.outbox.OutboxWriter;
import com.meridianbank.fraud.repository.AmlAlertRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The compliance case lifecycle: OPEN -> UNDER_REVIEW -> {CLEARED, ESCALATED} — see
 * docs/architecture/aml-flow.md. Only {@code COMPLIANCE_OFFICER}/{@code ADMIN} may transition an
 * AML alert (enforced in AmlAlertController); {@code RISK_ANALYST} may view but not clear/escalate.
 */
@Service
public class AmlAlertService {

    private final AmlAlertRepository repository;
    private final OutboxWriter outboxWriter;

    public AmlAlertService(AmlAlertRepository repository, OutboxWriter outboxWriter) {
        this.repository = repository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional(readOnly = true)
    public AmlAlert getOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(AmlAlertNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<AmlAlert> queue(AmlAlertStatus status, UUID customerId, Pageable pageable) {
        if (status != null) {
            return repository.findByStatus(status, pageable);
        }
        if (customerId != null) {
            return repository.findByCustomerId(customerId, pageable);
        }
        return repository.findAll(pageable);
    }

    @Transactional
    public AmlAlert startReview(UUID id, UUID reviewerId) {
        AmlAlert alert = getOrThrow(id);
        if (alert.getStatus() != AmlAlertStatus.OPEN) {
            throw new InvalidAlertTransitionException(
                    "Only an OPEN alert can be claimed for review (current status: " + alert.getStatus() + ")");
        }
        alert.startReview(reviewerId);
        return repository.save(alert);
    }

    @Transactional
    public AmlAlert clear(UUID id, UUID reviewerId, String notes) {
        return resolve(id, AmlAlertStatus.CLEARED, reviewerId, notes);
    }

    @Transactional
    public AmlAlert escalate(UUID id, UUID reviewerId, String notes) {
        return resolve(id, AmlAlertStatus.ESCALATED, reviewerId, notes);
    }

    private AmlAlert resolve(UUID id, AmlAlertStatus terminalStatus, UUID reviewerId, String notes) {
        AmlAlert alert = getOrThrow(id);
        if (alert.getStatus() != AmlAlertStatus.UNDER_REVIEW) {
            throw new InvalidAlertTransitionException(
                    "Only an alert UNDER_REVIEW can be resolved (current status: " + alert.getStatus() + ")");
        }
        alert.resolve(terminalStatus, reviewerId, notes);
        AmlAlert saved = repository.save(alert);
        outboxWriter.write("fraud.detected", "aml_alert", saved.getId(),
                new AmlAlertStatusChangedPayload(saved.getId(), saved.getTransactionId(), saved.getCustomerId(),
                        saved.getStatus().name()));
        return saved;
    }

    private record AmlAlertStatusChangedPayload(UUID alertId, UUID transactionId, UUID customerId, String status) {
    }
}
