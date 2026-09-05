package com.meridianbank.fraud.service;

import com.meridianbank.fraud.domain.FraudRule;
import com.meridianbank.fraud.domain.RuleCategory;
import com.meridianbank.fraud.domain.RuleCode;
import com.meridianbank.fraud.repository.FraudRuleRepository;
import com.meridianbank.fraud.repository.RiskAssessmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/** Proves each AML signal in docs/architecture/aml-flow.md fires independently of the fraud
 *  score — this evaluator never returns a decision, only a list of triggered signals. */
@ExtendWith(MockitoExtension.class)
class AmlSignalEvaluatorTest {

    @Mock private FraudRuleRepository fraudRuleRepository;
    @Mock private RiskAssessmentRepository riskAssessmentRepository;

    private AmlSignalEvaluator evaluator;

    private final UUID customerId = UUID.randomUUID();
    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID destinationAccountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        evaluator = new AmlSignalEvaluator(fraudRuleRepository, riskAssessmentRepository);
    }

    private RiskContext context(BigDecimal amount) {
        return new RiskContext(UUID.randomUUID(), customerId, sourceAccountId, destinationAccountId, amount, "USD");
    }

    @Test
    void evaluate_highValue_triggersAtOrAboveThreshold() {
        FraudRule rule = new FraudRule(RuleCode.HIGH_VALUE, "high value", 0, new BigDecimal("10000.00"), null, null, true);
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.AML)).thenReturn(List.of(rule));

        List<AmlSignalEvaluator.TriggeredSignal> triggered = evaluator.evaluate(context(new BigDecimal("10000.00")));

        assertThat(triggered).extracting(AmlSignalEvaluator.TriggeredSignal::signalCode)
                .containsExactly(RuleCode.HIGH_VALUE);
    }

    @Test
    void evaluate_highValue_doesNotTriggerBelowThreshold() {
        FraudRule rule = new FraudRule(RuleCode.HIGH_VALUE, "high value", 0, new BigDecimal("10000.00"), null, null, true);
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.AML)).thenReturn(List.of(rule));

        List<AmlSignalEvaluator.TriggeredSignal> triggered = evaluator.evaluate(context(new BigDecimal("9999.99")));

        assertThat(triggered).isEmpty();
    }

    @Test
    void evaluate_velocity_includesCurrentTransactionInRollingTotal() {
        FraudRule rule = new FraudRule(RuleCode.VELOCITY, "velocity", 0, new BigDecimal("20000.00"), 86_400, null, true);
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.AML)).thenReturn(List.of(rule));
        when(riskAssessmentRepository.sumAmountByCustomerIdSince(eq(customerId), any()))
                .thenReturn(new BigDecimal("19000.00"));

        List<AmlSignalEvaluator.TriggeredSignal> notYet = evaluator.evaluate(context(new BigDecimal("500.00")));
        assertThat(notYet).isEmpty(); // 19000 + 500 = 19500 < 20000

        List<AmlSignalEvaluator.TriggeredSignal> triggered = evaluator.evaluate(context(new BigDecimal("1000.00")));
        assertThat(triggered).extracting(AmlSignalEvaluator.TriggeredSignal::signalCode)
                .containsExactly(RuleCode.VELOCITY); // 19000 + 1000 = 20000 >= 20000
    }

    @Test
    void evaluate_structuring_countsTransactionsJustUnderTheCeilingIncludingThisOne() {
        // ceiling 10000, floor = 8000 (80%); 2 prior in-range + this one (9500) = 3 >= threshold 3
        FraudRule rule = new FraudRule(RuleCode.STRUCTURING, "structuring", 0, new BigDecimal("10000.00"),
                86_400, 3, true);
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.AML)).thenReturn(List.of(rule));
        when(riskAssessmentRepository.countByCustomerIdAndAmountBetweenAndCreatedAtAfter(
                eq(customerId), any(), any(), any())).thenReturn(2L);

        List<AmlSignalEvaluator.TriggeredSignal> triggered = evaluator.evaluate(context(new BigDecimal("9500.00")));

        assertThat(triggered).extracting(AmlSignalEvaluator.TriggeredSignal::signalCode)
                .containsExactly(RuleCode.STRUCTURING);
    }

    @Test
    void evaluate_structuring_doesNotTriggerBelowCountThreshold() {
        FraudRule rule = new FraudRule(RuleCode.STRUCTURING, "structuring", 0, new BigDecimal("10000.00"),
                86_400, 3, true);
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.AML)).thenReturn(List.of(rule));
        when(riskAssessmentRepository.countByCustomerIdAndAmountBetweenAndCreatedAtAfter(
                any(), any(), any(), any())).thenReturn(1L);

        List<AmlSignalEvaluator.TriggeredSignal> triggered = evaluator.evaluate(context(new BigDecimal("9500.00")));

        assertThat(triggered).isEmpty(); // 1 prior + this one = 2 < 3
    }

    @Test
    void evaluate_repeatedNewBeneficiary_countsPriorTransfersPlusThisOne() {
        FraudRule rule = new FraudRule(RuleCode.REPEATED_NEW_BENEFICIARY, "repeated", 0, new BigDecimal("3"),
                86_400, null, true);
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.AML)).thenReturn(List.of(rule));
        when(riskAssessmentRepository.countByCustomerIdAndDestinationAccountIdAndCreatedAtAfter(
                eq(customerId), eq(destinationAccountId), any())).thenReturn(2L);

        List<AmlSignalEvaluator.TriggeredSignal> triggered = evaluator.evaluate(context(new BigDecimal("100.00")));

        assertThat(triggered).extracting(AmlSignalEvaluator.TriggeredSignal::signalCode)
                .containsExactly(RuleCode.REPEATED_NEW_BENEFICIARY); // 2 prior + 1 = 3 >= 3
    }

    @Test
    void evaluate_multipleSignalsCanTriggerTogether() {
        FraudRule highValue = new FraudRule(RuleCode.HIGH_VALUE, "high value", 0, new BigDecimal("10000.00"),
                null, null, true);
        FraudRule velocity = new FraudRule(RuleCode.VELOCITY, "velocity", 0, new BigDecimal("5000.00"),
                86_400, null, true);
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.AML)).thenReturn(List.of(highValue, velocity));
        when(riskAssessmentRepository.sumAmountByCustomerIdSince(eq(customerId), any())).thenReturn(BigDecimal.ZERO);

        List<AmlSignalEvaluator.TriggeredSignal> triggered = evaluator.evaluate(context(new BigDecimal("10000.00")));

        assertThat(triggered).extracting(AmlSignalEvaluator.TriggeredSignal::signalCode)
                .containsExactlyInAnyOrder(RuleCode.HIGH_VALUE, RuleCode.VELOCITY);
    }
}
