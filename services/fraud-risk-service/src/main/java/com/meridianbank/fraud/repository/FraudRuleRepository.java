package com.meridianbank.fraud.repository;

import com.meridianbank.fraud.domain.FraudRule;
import com.meridianbank.fraud.domain.RuleCategory;
import com.meridianbank.fraud.domain.RuleCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FraudRuleRepository extends JpaRepository<FraudRule, UUID> {

    List<FraudRule> findByCategoryAndEnabledTrue(RuleCategory category);

    Optional<FraudRule> findByRuleCode(RuleCode ruleCode);
}
