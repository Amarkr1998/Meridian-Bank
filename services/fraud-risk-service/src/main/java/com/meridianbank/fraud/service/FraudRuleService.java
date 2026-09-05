package com.meridianbank.fraud.service;

import com.meridianbank.fraud.domain.FraudRule;
import com.meridianbank.fraud.exception.FraudRuleNotFoundException;
import com.meridianbank.fraud.repository.FraudRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Configuration management for {@link FraudRule}s — weights and thresholds are business data,
 * editable without a code deployment; see docs/governance/governance-principles.md ("Configuration
 * Governance"). Which rules exist is fixed at the code level ({@link com.meridianbank.fraud.domain.RuleCode}) —
 * this service only tunes/enables/disables the existing set, it never creates a new rule code.
 */
@Service
public class FraudRuleService {

    private final FraudRuleRepository repository;

    public FraudRuleService(FraudRuleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<FraudRule> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public FraudRule getOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(FraudRuleNotFoundException::new);
    }

    @Transactional
    public FraudRule update(UUID id, int weight, BigDecimal thresholdNumeric, Integer thresholdWindowSeconds,
                             Integer thresholdCount, boolean enabled, UUID updatedBy) {
        FraudRule rule = getOrThrow(id);
        rule.update(weight, thresholdNumeric, thresholdWindowSeconds, thresholdCount, enabled, updatedBy);
        return repository.save(rule);
    }
}
