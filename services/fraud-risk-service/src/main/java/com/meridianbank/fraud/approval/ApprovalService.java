package com.meridianbank.fraud.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridianbank.fraud.audit.AuditEventPublisher;
import com.meridianbank.fraud.domain.FraudAlertStatus;
import com.meridianbank.fraud.service.FraudAlertService;
import com.meridianbank.fraud.service.FraudRuleService;

import static com.meridianbank.fraud.audit.AuditAction.APPROVAL_COMPLETED;
import static com.meridianbank.fraud.audit.AuditAction.APPROVAL_CREATED;
import static com.meridianbank.fraud.audit.AuditAction.CONFIGURATION_CHANGED;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Maker-checker workflow for fraud-risk-service's two gated actions — see
 * docs/adr/0011-maker-checker.md. A maker creates a PENDING_APPROVAL request instead of the
 * action executing immediately; a different checker must approve it before the underlying
 * service method actually runs. Reject leaves the resource untouched.
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository repository;
    private final FraudAlertService fraudAlertService;
    private final FraudRuleService fraudRuleService;
    private final ObjectMapper objectMapper;
    private final AuditEventPublisher auditEventPublisher;

    public ApprovalService(ApprovalRequestRepository repository, FraudAlertService fraudAlertService,
                            FraudRuleService fraudRuleService, ObjectMapper objectMapper,
                            AuditEventPublisher auditEventPublisher) {
        this.repository = repository;
        this.fraudAlertService = fraudAlertService;
        this.fraudRuleService = fraudRuleService;
        this.objectMapper = objectMapper;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public ApprovalRequest requestAlertResolution(UUID alertId, FraudAlertStatus targetStatus, String notes,
                                                   UUID requestedBy) {
        if (repository.existsByResourceIdAndActionTypeAndStatus(alertId, ApprovalActionType.FRAUD_ALERT_RESOLUTION,
                ApprovalStatus.PENDING_APPROVAL)) {
            throw new DuplicatePendingApprovalException();
        }
        String payload = writeJson(new AlertResolutionPayload(targetStatus.name(), notes));
        ApprovalRequest request = repository.save(
                new ApprovalRequest(ApprovalActionType.FRAUD_ALERT_RESOLUTION, alertId, payload, requestedBy));
        auditEventPublisher.record(APPROVAL_CREATED, "approval_request", request.getId(), requestedBy, null,
                "PENDING_APPROVAL", "FRAUD_ALERT_RESOLUTION requested on " + alertId + " -> " + targetStatus);
        return request;
    }

    @Transactional
    public ApprovalRequest requestRuleUpdate(UUID ruleId, int weight, BigDecimal thresholdNumeric,
                                              Integer thresholdWindowSeconds, Integer thresholdCount,
                                              boolean enabled, UUID requestedBy) {
        if (repository.existsByResourceIdAndActionTypeAndStatus(ruleId, ApprovalActionType.FRAUD_RULE_UPDATE,
                ApprovalStatus.PENDING_APPROVAL)) {
            throw new DuplicatePendingApprovalException();
        }
        String payload = writeJson(new RuleUpdatePayload(weight, thresholdNumeric, thresholdWindowSeconds,
                thresholdCount, enabled));
        ApprovalRequest request = repository.save(
                new ApprovalRequest(ApprovalActionType.FRAUD_RULE_UPDATE, ruleId, payload, requestedBy));
        auditEventPublisher.record(APPROVAL_CREATED, "approval_request", request.getId(), requestedBy, null,
                "PENDING_APPROVAL", "FRAUD_RULE_UPDATE requested on " + ruleId + " (weight=" + weight + ")");
        return request;
    }

    @Transactional(readOnly = true)
    public ApprovalRequest getOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(ApprovalRequestNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<ApprovalRequest> queue(ApprovalStatus status, Pageable pageable) {
        return status != null ? repository.findByStatus(status, pageable) : repository.findAll(pageable);
    }

    @Transactional
    public ApprovalRequest approve(UUID id, UUID checkerId, String notes) {
        ApprovalRequest request = requirePending(id, checkerId);
        request.approve(checkerId, notes);
        repository.save(request);
        execute(request);
        auditEventPublisher.record(APPROVAL_COMPLETED, "approval_request", request.getId(), checkerId, null,
                "APPROVED", notes);
        return request;
    }

    @Transactional
    public ApprovalRequest reject(UUID id, UUID checkerId, String notes) {
        ApprovalRequest request = requirePending(id, checkerId);
        request.reject(checkerId, notes);
        ApprovalRequest saved = repository.save(request);
        auditEventPublisher.record(APPROVAL_COMPLETED, "approval_request", saved.getId(), checkerId, null,
                "REJECTED", notes);
        return saved;
    }

    private ApprovalRequest requirePending(UUID id, UUID checkerId) {
        ApprovalRequest request = getOrThrow(id);
        if (request.getStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new InvalidApprovalTransitionException(
                    "Only a PENDING_APPROVAL request can be decided (current status: " + request.getStatus() + ")");
        }
        if (request.getRequestedBy().equals(checkerId)) {
            throw new SelfApprovalNotAllowedException();
        }
        return request;
    }

    private void execute(ApprovalRequest request) {
        switch (request.getActionType()) {
            case FRAUD_ALERT_RESOLUTION -> {
                AlertResolutionPayload payload = readJson(request.getPayload(), AlertResolutionPayload.class);
                FraudAlertStatus target = FraudAlertStatus.valueOf(payload.targetStatus());
                UUID maker = request.getRequestedBy();
                switch (target) {
                    case CLEARED -> fraudAlertService.clear(request.getResourceId(), maker, payload.notes());
                    case ESCALATED -> fraudAlertService.escalate(request.getResourceId(), maker, payload.notes());
                    case CONFIRMED_FRAUD -> fraudAlertService.confirm(request.getResourceId(), maker, payload.notes());
                    default -> throw new IllegalStateException("Unsupported alert resolution target: " + target);
                }
            }
            case FRAUD_RULE_UPDATE -> {
                RuleUpdatePayload payload = readJson(request.getPayload(), RuleUpdatePayload.class);
                fraudRuleService.update(request.getResourceId(), payload.weight(), payload.thresholdNumeric(),
                        payload.thresholdWindowSeconds(), payload.thresholdCount(), payload.enabled(),
                        request.getRequestedBy());
                auditEventPublisher.record(CONFIGURATION_CHANGED, "fraud_rule", request.getResourceId(),
                        request.getDecidedBy(), null, "SUCCESS",
                        "weight=" + payload.weight() + ", enabled=" + payload.enabled());
            }
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize approval request payload", e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize approval request payload", e);
        }
    }

    private record AlertResolutionPayload(String targetStatus, String notes) {
    }

    private record RuleUpdatePayload(int weight, BigDecimal thresholdNumeric, Integer thresholdWindowSeconds,
                                      Integer thresholdCount, boolean enabled) {
    }
}
