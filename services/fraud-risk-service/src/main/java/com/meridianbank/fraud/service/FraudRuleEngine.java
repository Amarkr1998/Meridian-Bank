package com.meridianbank.fraud.service;

import com.meridianbank.fraud.domain.FraudRule;
import com.meridianbank.fraud.domain.RiskDecision;
import com.meridianbank.fraud.domain.RuleCategory;
import com.meridianbank.fraud.domain.RuleCode;
import com.meridianbank.fraud.repository.FraudRuleRepository;
import com.meridianbank.fraud.repository.RiskAssessmentRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates the configured FRAUD-category {@link FraudRule}s against a {@link RiskContext} and
 * this customer's own risk-assessment history (there is no other source of transaction history
 * available to this service). Scoring bands come from
 * docs/architecture/fraud-flow.md: 0-30 LOW/ALLOW, 31-70 MEDIUM/REVIEW, 71-100 HIGH/BLOCK.
 */
@Component
public class FraudRuleEngine {

    private final FraudRuleRepository fraudRuleRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;

    public FraudRuleEngine(FraudRuleRepository fraudRuleRepository,
                            RiskAssessmentRepository riskAssessmentRepository) {
        this.fraudRuleRepository = fraudRuleRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
    }

    public Evaluation evaluate(RiskContext context) {
        List<FraudRule> rules = fraudRuleRepository.findByCategoryAndEnabledTrue(RuleCategory.FRAUD);
        int score = 0;
        List<RuleCode> hits = new ArrayList<>();
        for (FraudRule rule : rules) {
            if (isTriggered(rule, context)) {
                score += rule.getWeight();
                hits.add(rule.getRuleCode());
            }
        }
        score = Math.min(score, 100);
        RiskDecision decision = score >= 71 ? RiskDecision.BLOCK : score >= 31 ? RiskDecision.REVIEW : RiskDecision.ALLOW;
        return new Evaluation(score, decision, hits);
    }

    private boolean isTriggered(FraudRule rule, RiskContext context) {
        Instant window = rule.getThresholdWindowSeconds() != null
                ? Instant.now().minusSeconds(rule.getThresholdWindowSeconds()) : null;
        return switch (rule.getRuleCode()) {
            case HIGH_AMOUNT -> context.amount().compareTo(rule.getThresholdNumeric()) >= 0;
            case HIGH_VELOCITY, RAPID_SEQUENTIAL_TRANSFERS ->
                    riskAssessmentRepository.countByCustomerIdAndCreatedAtAfter(context.customerId(), window)
                            >= rule.getThresholdNumeric().longValue();
            case NEW_BENEFICIARY_HIGH_AMOUNT -> context.destinationAccountId() != null
                    && !riskAssessmentRepository.existsByCustomerIdAndDestinationAccountId(
                            context.customerId(), context.destinationAccountId())
                    && context.amount().compareTo(rule.getThresholdNumeric()) >= 0;
            case REPEAT_RISKY_BEHAVIOR -> riskAssessmentRepository.countByCustomerIdAndDecisionInAndCreatedAtAfter(
                    context.customerId(), List.of(RiskDecision.REVIEW, RiskDecision.BLOCK), window)
                    >= rule.getThresholdNumeric().longValue();
            default -> false; // AML rule codes never reach here — different category.
        };
    }

    public record Evaluation(int score, RiskDecision decision, List<RuleCode> ruleHits) {
    }
}
