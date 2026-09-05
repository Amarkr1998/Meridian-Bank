package com.meridianbank.fraud.service;

import com.meridianbank.fraud.domain.FraudRule;
import com.meridianbank.fraud.domain.RiskDecision;
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

/** Proves the scoring/decision logic documented in docs/architecture/fraud-flow.md in isolation
 *  from the database, with each rule's triggering condition mocked directly. */
@ExtendWith(MockitoExtension.class)
class FraudRuleEngineTest {

    @Mock private FraudRuleRepository fraudRuleRepository;
    @Mock private RiskAssessmentRepository riskAssessmentRepository;

    private FraudRuleEngine engine;

    private final UUID customerId = UUID.randomUUID();
    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID destinationAccountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new FraudRuleEngine(fraudRuleRepository, riskAssessmentRepository);
    }

    private RiskContext context(BigDecimal amount) {
        return new RiskContext(UUID.randomUUID(), customerId, sourceAccountId, destinationAccountId, amount, "USD");
    }

    private FraudRule highAmountRule(int weight) {
        return new FraudRule(RuleCode.HIGH_AMOUNT, "high amount", weight, new BigDecimal("5000.00"), null, null, true);
    }

    @Test
    void evaluate_noRulesTriggered_returnsAllowWithZeroScore() {
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.FRAUD)).thenReturn(List.of(highAmountRule(40)));

        FraudRuleEngine.Evaluation result = engine.evaluate(context(new BigDecimal("100.00")));

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.decision()).isEqualTo(RiskDecision.ALLOW);
        assertThat(result.ruleHits()).isEmpty();
    }

    @Test
    void evaluate_highAmountTriggered_addsWeightAndReturnsReview() {
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.FRAUD)).thenReturn(List.of(highAmountRule(40)));

        FraudRuleEngine.Evaluation result = engine.evaluate(context(new BigDecimal("9000.00")));

        assertThat(result.score()).isEqualTo(40);
        assertThat(result.decision()).isEqualTo(RiskDecision.REVIEW); // 31-70 band
        assertThat(result.ruleHits()).containsExactly(RuleCode.HIGH_AMOUNT);
    }

    @Test
    void evaluate_multipleRulesTriggered_scoreAccumulatesAndCanReachBlock() {
        FraudRule highAmount = highAmountRule(40);
        FraudRule rapidSequential = new FraudRule(RuleCode.RAPID_SEQUENTIAL_TRANSFERS, "rapid", 45,
                new BigDecimal("3"), 60, null, true);
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.FRAUD))
                .thenReturn(List.of(highAmount, rapidSequential));
        when(riskAssessmentRepository.countByCustomerIdAndCreatedAtAfter(eq(customerId), any())).thenReturn(3L);

        FraudRuleEngine.Evaluation result = engine.evaluate(context(new BigDecimal("9000.00")));

        assertThat(result.score()).isEqualTo(85); // 40 + 45
        assertThat(result.decision()).isEqualTo(RiskDecision.BLOCK); // 71-100 band
        assertThat(result.ruleHits()).containsExactlyInAnyOrder(RuleCode.HIGH_AMOUNT, RuleCode.RAPID_SEQUENTIAL_TRANSFERS);
    }

    @Test
    void evaluate_scoreNeverExceedsOneHundredEvenIfWeightsSumHigher() {
        FraudRule a = new FraudRule(RuleCode.HIGH_AMOUNT, "a", 70, new BigDecimal("1.00"), null, null, true);
        FraudRule b = new FraudRule(RuleCode.RAPID_SEQUENTIAL_TRANSFERS, "b", 60, new BigDecimal("1"), 60, null, true);
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.FRAUD)).thenReturn(List.of(a, b));
        when(riskAssessmentRepository.countByCustomerIdAndCreatedAtAfter(eq(customerId), any())).thenReturn(5L);

        FraudRuleEngine.Evaluation result = engine.evaluate(context(new BigDecimal("100.00")));

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.decision()).isEqualTo(RiskDecision.BLOCK);
    }

    @Test
    void evaluate_disabledRulesAreNeverEvaluated() {
        // findByCategoryAndEnabledTrue itself excludes disabled rules — this test proves the
        // engine relies on that repository filter and doesn't re-check `enabled` itself, i.e. an
        // empty result set means nothing is evaluated.
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.FRAUD)).thenReturn(List.of());

        FraudRuleEngine.Evaluation result = engine.evaluate(context(new BigDecimal("999999.00")));

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.decision()).isEqualTo(RiskDecision.ALLOW);
    }

    @Test
    void evaluate_newBeneficiaryHighAmount_triggersOnlyWhenBothConditionsHold() {
        FraudRule rule = new FraudRule(RuleCode.NEW_BENEFICIARY_HIGH_AMOUNT, "new beneficiary", 25,
                new BigDecimal("1000.00"), null, null, true);
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.FRAUD)).thenReturn(List.of(rule));
        when(riskAssessmentRepository.existsByCustomerIdAndDestinationAccountId(customerId, destinationAccountId))
                .thenReturn(false); // never transferred to this destination before

        FraudRuleEngine.Evaluation triggered = engine.evaluate(context(new BigDecimal("1500.00")));
        assertThat(triggered.ruleHits()).containsExactly(RuleCode.NEW_BENEFICIARY_HIGH_AMOUNT);

        FraudRuleEngine.Evaluation belowAmount = engine.evaluate(context(new BigDecimal("50.00")));
        assertThat(belowAmount.ruleHits()).isEmpty();
    }

    @Test
    void evaluate_newBeneficiaryHighAmount_doesNotTriggerForAKnownBeneficiary() {
        FraudRule rule = new FraudRule(RuleCode.NEW_BENEFICIARY_HIGH_AMOUNT, "new beneficiary", 25,
                new BigDecimal("1000.00"), null, null, true);
        when(fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.FRAUD)).thenReturn(List.of(rule));
        when(riskAssessmentRepository.existsByCustomerIdAndDestinationAccountId(customerId, destinationAccountId))
                .thenReturn(true); // this customer has paid this destination before

        FraudRuleEngine.Evaluation result = engine.evaluate(context(new BigDecimal("5000.00")));

        assertThat(result.ruleHits()).isEmpty();
    }
}
