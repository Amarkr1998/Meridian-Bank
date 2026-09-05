package com.meridianbank.fraud.service;

import com.meridianbank.fraud.domain.FraudRule;
import com.meridianbank.fraud.domain.RuleCategory;
import com.meridianbank.fraud.domain.RuleCode;
import com.meridianbank.fraud.repository.FraudRuleRepository;
import com.meridianbank.fraud.repository.RiskAssessmentRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates the configured AML-category {@link FraudRule}s ("signals" in AML terminology — see
 * docs/architecture/aml-flow.md) against a {@link RiskContext} and this customer's own
 * risk-assessment history. Independent of {@link FraudRuleEngine}: AML signals never affect the
 * fraud score or the payment's own outcome, they only raise a compliance case — see
 * docs/governance/governance-principles.md ("Simplified AML Disclaimer").
 */
@Component
public class AmlSignalEvaluator {

    private static final BigDecimal STRUCTURING_LOWER_BAND = new BigDecimal("0.80");

    private final FraudRuleRepository fraudRuleRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;

    public AmlSignalEvaluator(FraudRuleRepository fraudRuleRepository,
                               RiskAssessmentRepository riskAssessmentRepository) {
        this.fraudRuleRepository = fraudRuleRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
    }

    public List<TriggeredSignal> evaluate(RiskContext context) {
        List<FraudRule> rules = fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.AML);
        List<TriggeredSignal> triggered = new ArrayList<>();
        for (FraudRule rule : rules) {
            String details = check(rule, context);
            if (details != null) {
                triggered.add(new TriggeredSignal(rule.getRuleCode(), details));
            }
        }
        return triggered;
    }

    private String check(FraudRule rule, RiskContext context) {
        Instant window = rule.getThresholdWindowSeconds() != null
                ? Instant.now().minusSeconds(rule.getThresholdWindowSeconds()) : null;
        return switch (rule.getRuleCode()) {
            case HIGH_VALUE -> context.amount().compareTo(rule.getThresholdNumeric()) >= 0
                    ? "Amount %s >= threshold %s".formatted(context.amount(), rule.getThresholdNumeric())
                    : null;
            case VELOCITY -> {
                BigDecimal rollingTotal = riskAssessmentRepository
                        .sumAmountByCustomerIdSince(context.customerId(), window)
                        .add(context.amount());
                yield rollingTotal.compareTo(rule.getThresholdNumeric()) >= 0
                        ? "Rolling volume %s >= threshold %s over %ds".formatted(
                                rollingTotal, rule.getThresholdNumeric(), rule.getThresholdWindowSeconds())
                        : null;
            }
            case STRUCTURING -> {
                BigDecimal ceiling = rule.getThresholdNumeric();
                BigDecimal floor = ceiling.multiply(STRUCTURING_LOWER_BAND);
                long priorCount = riskAssessmentRepository.countByCustomerIdAndAmountBetweenAndCreatedAtAfter(
                        context.customerId(), floor, ceiling, window);
                boolean thisOneInRange = context.amount().compareTo(floor) >= 0
                        && context.amount().compareTo(ceiling) < 0;
                long effectiveCount = priorCount + (thisOneInRange ? 1 : 0);
                int countThreshold = rule.getThresholdCount() != null ? rule.getThresholdCount() : Integer.MAX_VALUE;
                yield effectiveCount >= countThreshold
                        ? "%d transactions in [%s, %s) within %ds (possible structuring)".formatted(
                                effectiveCount, floor, ceiling, rule.getThresholdWindowSeconds())
                        : null;
            }
            case REPEATED_NEW_BENEFICIARY -> {
                if (context.destinationAccountId() == null) {
                    yield null;
                }
                long priorCount = riskAssessmentRepository.countByCustomerIdAndDestinationAccountIdAndCreatedAtAfter(
                        context.customerId(), context.destinationAccountId(), window);
                long effectiveCount = priorCount + 1;
                yield effectiveCount >= rule.getThresholdNumeric().longValue()
                        ? "%d transfers to the same destination account within %ds".formatted(
                                effectiveCount, rule.getThresholdWindowSeconds())
                        : null;
            }
            default -> null; // FRAUD rule codes never reach here — different category.
        };
    }

    public record TriggeredSignal(RuleCode signalCode, String details) {
    }
}
