package com.meridianbank.fraud.service;

import com.meridianbank.fraud.domain.FraudAlert;
import com.meridianbank.fraud.domain.FraudAlertStatus;
import com.meridianbank.fraud.exception.FraudAlertNotFoundException;
import com.meridianbank.fraud.exception.InvalidAlertTransitionException;
import com.meridianbank.fraud.outbox.OutboxWriter;
import com.meridianbank.fraud.repository.FraudAlertRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The review queue lifecycle: OPEN -> UNDER_REVIEW -> {CLEARED, ESCALATED, CONFIRMED_FRAUD} — see
 * docs/architecture/fraud-flow.md ("Review / Escalate / Block / Clear"). Single-actor for this
 * phase: maker-checker segregation of duties for high-risk fraud decisions is Phase 10 — see
 * docs/governance/governance-principles.md.
 */
@Service
public class FraudAlertService {

    private final FraudAlertRepository repository;
    private final OutboxWriter outboxWriter;

    public FraudAlertService(FraudAlertRepository repository, OutboxWriter outboxWriter) {
        this.repository = repository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional(readOnly = true)
    public FraudAlert getOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(FraudAlertNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<FraudAlert> queue(FraudAlertStatus status, UUID customerId, Pageable pageable) {
        if (status != null) {
            return repository.findByStatus(status, pageable);
        }
        if (customerId != null) {
            return repository.findByCustomerId(customerId, pageable);
        }
        return repository.findAll(pageable);
    }

    @Transactional
    public FraudAlert startReview(UUID id, UUID reviewerId) {
        FraudAlert alert = getOrThrow(id);
        if (alert.getStatus() != FraudAlertStatus.OPEN) {
            throw new InvalidAlertTransitionException(
                    "Only an OPEN alert can be claimed for review (current status: " + alert.getStatus() + ")");
        }
        alert.startReview(reviewerId);
        return repository.save(alert);
    }

    @Transactional
    public FraudAlert clear(UUID id, UUID reviewerId, String notes) {
        return resolve(id, FraudAlertStatus.CLEARED, reviewerId, notes);
    }

    @Transactional
    public FraudAlert escalate(UUID id, UUID reviewerId, String notes) {
        return resolve(id, FraudAlertStatus.ESCALATED, reviewerId, notes);
    }

    @Transactional
    public FraudAlert confirm(UUID id, UUID reviewerId, String notes) {
        return resolve(id, FraudAlertStatus.CONFIRMED_FRAUD, reviewerId, notes);
    }

    private FraudAlert resolve(UUID id, FraudAlertStatus terminalStatus, UUID reviewerId, String notes) {
        FraudAlert alert = getOrThrow(id);
        if (alert.getStatus() != FraudAlertStatus.UNDER_REVIEW) {
            throw new InvalidAlertTransitionException(
                    "Only an alert UNDER_REVIEW can be resolved (current status: " + alert.getStatus() + ")");
        }
        alert.resolve(terminalStatus, reviewerId, notes);
        FraudAlert saved = repository.save(alert);
        outboxWriter.write("fraud.detected", "fraud_alert", saved.getId(),
                new FraudAlertStatusChangedPayload(saved.getId(), saved.getTransactionId(), saved.getCustomerId(),
                        saved.getStatus().name()));
        return saved;
    }

    private record FraudAlertStatusChangedPayload(UUID alertId, UUID transactionId, UUID customerId, String status) {
    }
}
