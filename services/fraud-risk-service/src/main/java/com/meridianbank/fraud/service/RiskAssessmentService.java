package com.meridianbank.fraud.service;

import com.meridianbank.fraud.audit.AuditAction;
import com.meridianbank.fraud.audit.AuditEventPublisher;
import com.meridianbank.fraud.domain.*;
import com.meridianbank.fraud.outbox.OutboxWriter;
import com.meridianbank.fraud.repository.AmlAlertRepository;
import com.meridianbank.fraud.repository.FraudAlertRepository;
import com.meridianbank.fraud.repository.RiskAssessmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates a single {@code POST /api/v1/risk-assessments} call: runs the fraud rule engine and
 * the AML signal evaluator against the same {@link RiskContext}, persists the append-only
 * {@link RiskAssessment} record (this service's only source of transaction history — see its
 * Javadoc), opens a {@link FraudAlert} for any REVIEW/BLOCK decision and an {@link AmlAlert} for
 * every triggered AML signal, and publishes {@code fraud.detected} for each alert created. See
 * docs/architecture/fraud-flow.md and aml-flow.md.
 */
@Service
public class RiskAssessmentService {

    private final FraudRuleEngine fraudRuleEngine;
    private final AmlSignalEvaluator amlSignalEvaluator;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final AmlAlertRepository amlAlertRepository;
    private final OutboxWriter outboxWriter;
    private final AuditEventPublisher auditEventPublisher;

    public RiskAssessmentService(FraudRuleEngine fraudRuleEngine, AmlSignalEvaluator amlSignalEvaluator,
                                  RiskAssessmentRepository riskAssessmentRepository,
                                  FraudAlertRepository fraudAlertRepository, AmlAlertRepository amlAlertRepository,
                                  OutboxWriter outboxWriter, AuditEventPublisher auditEventPublisher) {
        this.fraudRuleEngine = fraudRuleEngine;
        this.amlSignalEvaluator = amlSignalEvaluator;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.amlAlertRepository = amlAlertRepository;
        this.outboxWriter = outboxWriter;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public RiskAssessment assess(RiskContext context) {
        FraudRuleEngine.Evaluation evaluation = fraudRuleEngine.evaluate(context);
        List<AmlSignalEvaluator.TriggeredSignal> amlSignals = amlSignalEvaluator.evaluate(context);

        String ruleHitsJoined = evaluation.ruleHits().stream().map(Enum::name).collect(Collectors.joining(","));
        RiskAssessment assessment = new RiskAssessment(context.transactionId(), context.customerId(),
                context.sourceAccountId(), context.destinationAccountId(), context.amount(), context.currency(),
                evaluation.score(), evaluation.decision(), ruleHitsJoined);
        riskAssessmentRepository.save(assessment);

        if (evaluation.decision() != RiskDecision.ALLOW) {
            FraudAlert alert = new FraudAlert(context.transactionId(), context.customerId(),
                    context.sourceAccountId(), context.destinationAccountId(), context.amount(), context.currency(),
                    evaluation.score(), evaluation.decision(), ruleHitsJoined);
            fraudAlertRepository.save(alert);
            outboxWriter.write("fraud.detected", "fraud_alert", alert.getId(),
                    new FraudDetectedPayload(alert.getId(), context.transactionId(), context.customerId(),
                            "FRAUD", evaluation.decision().name(), evaluation.score(), ruleHitsJoined));
            auditEventPublisher.record(AuditAction.FRAUD_ALERT_CREATED, "fraud_alert", alert.getId(), null, null,
                    evaluation.decision().name(),
                    "customer " + context.customerId() + ", score " + evaluation.score() + " (" + ruleHitsJoined + ")");
        }

        for (AmlSignalEvaluator.TriggeredSignal signal : amlSignals) {
            AmlAlert alert = new AmlAlert(context.transactionId(), context.customerId(), signal.signalCode(),
                    context.amount(), context.currency(), signal.details());
            amlAlertRepository.save(alert);
            outboxWriter.write("fraud.detected", "aml_alert", alert.getId(),
                    new FraudDetectedPayload(alert.getId(), context.transactionId(), context.customerId(),
                            "AML", signal.signalCode().name(), null, signal.details()));
            auditEventPublisher.record(AuditAction.AML_ALERT_CREATED, "aml_alert", alert.getId(), null, null,
                    signal.signalCode().name(), "customer " + context.customerId() + ", " + signal.details());
        }

        return assessment;
    }

    @Transactional(readOnly = true)
    public RiskSummary summaryFor(UUID customerId) {
        List<RiskAssessment> history = riskAssessmentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        int highestScore = history.stream().mapToInt(RiskAssessment::getScore).max().orElse(0);
        long openFraudAlerts = fraudAlertRepository.countByCustomerIdAndStatus(customerId, FraudAlertStatus.OPEN)
                + fraudAlertRepository.countByCustomerIdAndStatus(customerId, FraudAlertStatus.UNDER_REVIEW);
        long openAmlAlerts = amlAlertRepository.countByCustomerIdAndStatus(customerId, AmlAlertStatus.OPEN)
                + amlAlertRepository.countByCustomerIdAndStatus(customerId, AmlAlertStatus.UNDER_REVIEW);
        Instant lastAssessedAt = history.isEmpty() ? null : history.get(0).getCreatedAt();
        return new RiskSummary(customerId, history.size(), highestScore, openFraudAlerts, openAmlAlerts,
                lastAssessedAt);
    }

    public record RiskSummary(UUID customerId, int assessmentCount, int highestScoreSeen, long openFraudAlerts,
                               long openAmlAlerts, Instant lastAssessedAt) {
    }

    private record FraudDetectedPayload(UUID alertId, UUID transactionId, UUID customerId, String category,
                                         String decisionOrSignal, Integer score, String detail) {
    }
}
