package com.meridianbank.fraud.web.dto;

import com.meridianbank.fraud.domain.FraudRule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudRuleResponse(
        UUID id, String ruleCode, String category, String description, int weight, BigDecimal thresholdNumeric,
        Integer thresholdWindowSeconds, Integer thresholdCount, boolean enabled, Instant updatedAt, UUID updatedBy
) {
    public static FraudRuleResponse from(FraudRule r) {
        return new FraudRuleResponse(r.getId(), r.getRuleCode().name(), r.getCategory().name(), r.getDescription(),
                r.getWeight(), r.getThresholdNumeric(), r.getThresholdWindowSeconds(), r.getThresholdCount(),
                r.isEnabled(), r.getUpdatedAt(), r.getUpdatedBy());
    }
}
