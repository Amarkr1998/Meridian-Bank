package com.meridianbank.fraud.web.dto;

import com.meridianbank.fraud.domain.RiskAssessment;

import java.util.UUID;

public record RiskAssessmentResponse(UUID transactionId, int score, String decision, String ruleHits) {
    public static RiskAssessmentResponse from(RiskAssessment assessment) {
        return new RiskAssessmentResponse(assessment.getTransactionId(), assessment.getScore(),
                assessment.getDecision().name(), assessment.getRuleHits());
    }
}
