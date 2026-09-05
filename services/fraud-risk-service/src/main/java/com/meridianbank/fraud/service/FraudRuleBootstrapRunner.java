package com.meridianbank.fraud.service;

import com.meridianbank.fraud.domain.FraudRule;
import com.meridianbank.fraud.domain.RuleCode;
import com.meridianbank.fraud.repository.FraudRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the default rule set on first startup so the rule engine has something to evaluate out of
 * the box — same idempotent bootstrap pattern as auth-service's AdminBootstrapRunner (skipped
 * whenever any rule already exists). Every seeded value is ordinary business data from here on,
 * editable via {@code PATCH /api/v1/fraud-rules/{id}} without a code deployment — see
 * docs/governance/governance-principles.md ("Configuration Governance"). These are reasonable demo
 * defaults, not tuned production thresholds.
 */
@Component
public class FraudRuleBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FraudRuleBootstrapRunner.class);

    private final FraudRuleRepository repository;

    public FraudRuleBootstrapRunner(FraudRuleRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            log.info("Fraud rule bootstrap skipped: rules already exist");
            return;
        }

        List<FraudRule> defaults = List.of(
                new FraudRule(RuleCode.HIGH_AMOUNT,
                        "Single transaction amount at or above the configured threshold",
                        40, new BigDecimal("5000.00"), null, null, true),
                new FraudRule(RuleCode.HIGH_VELOCITY,
                        "Too many assessed transactions for this customer within the trailing window",
                        30, new BigDecimal("5"), 3600, null, true),
                new FraudRule(RuleCode.NEW_BENEFICIARY_HIGH_AMOUNT,
                        "First-ever transfer to this destination account, at or above the threshold",
                        25, new BigDecimal("1000.00"), null, null, true),
                new FraudRule(RuleCode.REPEAT_RISKY_BEHAVIOR,
                        "Multiple REVIEW/BLOCK decisions for this customer within the trailing window",
                        35, new BigDecimal("2"), 2_592_000, null, true),
                new FraudRule(RuleCode.RAPID_SEQUENTIAL_TRANSFERS,
                        "Too many assessed transactions for this customer within a short trailing window",
                        45, new BigDecimal("3"), 60, null, true),
                new FraudRule(RuleCode.HIGH_VALUE,
                        "Single transaction amount at or above the AML high-value threshold",
                        0, new BigDecimal("10000.00"), null, null, true),
                new FraudRule(RuleCode.VELOCITY,
                        "Rolling transaction volume for this customer at or above the AML threshold",
                        0, new BigDecimal("20000.00"), 86_400, null, true),
                new FraudRule(RuleCode.STRUCTURING,
                        "Multiple transactions clustered just under the AML high-value threshold",
                        0, new BigDecimal("10000.00"), 86_400, 3, true),
                new FraudRule(RuleCode.REPEATED_NEW_BENEFICIARY,
                        "Repeated transfers to the same destination account within the trailing window",
                        0, new BigDecimal("3"), 86_400, null, true)
        );
        repository.saveAll(defaults);
        log.info("Fraud rule bootstrap: seeded {} default rules", defaults.size());
    }
}
