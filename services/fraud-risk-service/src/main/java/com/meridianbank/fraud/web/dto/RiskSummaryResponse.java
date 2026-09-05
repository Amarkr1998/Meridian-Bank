package com.meridianbank.fraud.web.dto;

import com.meridianbank.fraud.service.RiskAssessmentService;

import java.time.Instant;
import java.util.UUID;

public record RiskSummaryResponse(UUID customerId, int assessmentCount, int highestScoreSeen, long openFraudAlerts,
                                   long openAmlAlerts, Instant lastAssessedAt) {
    public static RiskSummaryResponse from(RiskAssessmentService.RiskSummary summary) {
        return new RiskSummaryResponse(summary.customerId(), summary.assessmentCount(), summary.highestScoreSeen(),
                summary.openFraudAlerts(), summary.openAmlAlerts(), summary.lastAssessedAt());
    }
}
