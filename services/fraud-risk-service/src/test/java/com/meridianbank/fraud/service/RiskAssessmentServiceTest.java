package com.meridianbank.fraud.service;

import com.meridianbank.fraud.audit.AuditEventPublisher;
import com.meridianbank.fraud.domain.*;
import com.meridianbank.fraud.outbox.OutboxWriter;
import com.meridianbank.fraud.repository.AmlAlertRepository;
import com.meridianbank.fraud.repository.FraudAlertRepository;
import com.meridianbank.fraud.repository.RiskAssessmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Proves the orchestration: every assessment is recorded, an ALLOW never creates a fraud alert, a
 *  REVIEW/BLOCK always does, every triggered AML signal creates its own alert independently, and
 *  fraud.detected is published (via OutboxWriter) for each alert created — never for a plain ALLOW
 *  with no AML signals. */
@ExtendWith(MockitoExtension.class)
class RiskAssessmentServiceTest {

    @Mock private FraudRuleEngine fraudRuleEngine;
    @Mock private AmlSignalEvaluator amlSignalEvaluator;
    @Mock private RiskAssessmentRepository riskAssessmentRepository;
    @Mock private FraudAlertRepository fraudAlertRepository;
    @Mock private AmlAlertRepository amlAlertRepository;
    @Mock private OutboxWriter outboxWriter;
    @Mock private AuditEventPublisher auditEventPublisher;

    private RiskAssessmentService service;

    private final UUID transactionId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID destinationAccountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RiskAssessmentService(fraudRuleEngine, amlSignalEvaluator, riskAssessmentRepository,
                fraudAlertRepository, amlAlertRepository, outboxWriter, auditEventPublisher);
    }

    private RiskContext context() {
        return new RiskContext(transactionId, customerId, sourceAccountId, destinationAccountId,
                new BigDecimal("100.00"), "USD");
    }

    @Test
    void assess_allowWithNoAmlSignals_recordsHistoryButCreatesNoAlerts() {
        when(fraudRuleEngine.evaluate(any())).thenReturn(
                new FraudRuleEngine.Evaluation(0, RiskDecision.ALLOW, List.of()));
        when(amlSignalEvaluator.evaluate(any())).thenReturn(List.of());

        RiskAssessment result = service.assess(context());

        assertThat(result.getDecision()).isEqualTo(RiskDecision.ALLOW);
        verify(riskAssessmentRepository).save(any());
        verifyNoInteractions(fraudAlertRepository, amlAlertRepository);
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void assess_reviewDecision_createsFraudAlertAndPublishesFraudDetected() {
        when(fraudRuleEngine.evaluate(any())).thenReturn(
                new FraudRuleEngine.Evaluation(50, RiskDecision.REVIEW, List.of(RuleCode.HIGH_AMOUNT)));
        when(amlSignalEvaluator.evaluate(any())).thenReturn(List.of());

        service.assess(context());

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(fraudAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FraudAlertStatus.OPEN);
        assertThat(captor.getValue().getDecision()).isEqualTo(RiskDecision.REVIEW);
        verify(outboxWriter).write(eq("fraud.detected"), eq("fraud_alert"), any(), any());
    }

    @Test
    void assess_blockDecision_createsFraudAlert() {
        when(fraudRuleEngine.evaluate(any())).thenReturn(
                new FraudRuleEngine.Evaluation(90, RiskDecision.BLOCK, List.of(RuleCode.RAPID_SEQUENTIAL_TRANSFERS)));
        when(amlSignalEvaluator.evaluate(any())).thenReturn(List.of());

        service.assess(context());

        verify(fraudAlertRepository).save(any());
        verify(outboxWriter).write(eq("fraud.detected"), eq("fraud_alert"), any(), any());
    }

    @Test
    void assess_triggeredAmlSignals_eachCreatesItsOwnAlertIndependentlyOfFraudDecision() {
        when(fraudRuleEngine.evaluate(any())).thenReturn(
                new FraudRuleEngine.Evaluation(0, RiskDecision.ALLOW, List.of())); // fraud says ALLOW
        when(amlSignalEvaluator.evaluate(any())).thenReturn(List.of(
                new AmlSignalEvaluator.TriggeredSignal(RuleCode.HIGH_VALUE, "amount >= threshold"),
                new AmlSignalEvaluator.TriggeredSignal(RuleCode.VELOCITY, "rolling volume >= threshold")));

        RiskAssessment result = service.assess(context());

        // AML alerts exist even though the fraud decision was ALLOW — the two are independent.
        assertThat(result.getDecision()).isEqualTo(RiskDecision.ALLOW);
        verifyNoInteractions(fraudAlertRepository);
        verify(amlAlertRepository, times(2)).save(any());
        verify(outboxWriter, times(2)).write(eq("fraud.detected"), eq("aml_alert"), any(), any());
    }

    @Test
    void summaryFor_aggregatesHistoryAndOpenAlertCounts() {
        RiskAssessment a1 = new RiskAssessment(UUID.randomUUID(), customerId, sourceAccountId, destinationAccountId,
                new BigDecimal("10.00"), "USD", 20, RiskDecision.ALLOW, "");
        RiskAssessment a2 = new RiskAssessment(UUID.randomUUID(), customerId, sourceAccountId, destinationAccountId,
                new BigDecimal("9000.00"), "USD", 80, RiskDecision.BLOCK, "HIGH_AMOUNT");
        when(riskAssessmentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)).thenReturn(List.of(a2, a1));
        when(fraudAlertRepository.countByCustomerIdAndStatus(customerId, FraudAlertStatus.OPEN)).thenReturn(1L);
        when(fraudAlertRepository.countByCustomerIdAndStatus(customerId, FraudAlertStatus.UNDER_REVIEW)).thenReturn(0L);
        when(amlAlertRepository.countByCustomerIdAndStatus(customerId, AmlAlertStatus.OPEN)).thenReturn(0L);
        when(amlAlertRepository.countByCustomerIdAndStatus(customerId, AmlAlertStatus.UNDER_REVIEW)).thenReturn(0L);

        RiskAssessmentService.RiskSummary summary = service.summaryFor(customerId);

        assertThat(summary.assessmentCount()).isEqualTo(2);
        assertThat(summary.highestScoreSeen()).isEqualTo(80);
        assertThat(summary.openFraudAlerts()).isEqualTo(1);
        assertThat(summary.openAmlAlerts()).isEqualTo(0);
    }
}
