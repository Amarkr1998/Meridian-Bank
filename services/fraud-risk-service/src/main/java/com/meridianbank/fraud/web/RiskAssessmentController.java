package com.meridianbank.fraud.web;

import com.meridianbank.fraud.domain.RiskAssessment;
import com.meridianbank.fraud.service.RiskAssessmentService;
import com.meridianbank.fraud.service.RiskContext;
import com.meridianbank.fraud.web.dto.RiskAssessmentRequest;
import com.meridianbank.fraud.web.dto.RiskAssessmentResponse;
import com.meridianbank.fraud.web.dto.RiskSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * {@code POST /risk-assessments} performs **no ownership or business-authorization checks of its
 * own** — it trusts the caller (currently only payment-service) to have already validated that an
 * assessment should happen, exactly the same trust boundary as ledger-service's postings endpoint
 * (see ledger-service/README.md). It only verifies the caller holds a valid JWT (see
 * SecurityConfig). This is explicitly *not* the final security boundary for payments —
 * payment-service is.
 */
@RestController
@RequestMapping("/api/v1")
public class RiskAssessmentController {

    private final RiskAssessmentService riskAssessmentService;

    public RiskAssessmentController(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    @PostMapping("/risk-assessments")
    public RiskAssessmentResponse assess(@Valid @RequestBody RiskAssessmentRequest request) {
        RiskContext context = new RiskContext(request.transactionId(), request.customerId(),
                request.sourceAccountId(), request.destinationAccountId(), request.amount(),
                request.currency().toUpperCase());
        RiskAssessment assessment = riskAssessmentService.assess(context);
        return RiskAssessmentResponse.from(assessment);
    }

    /**
     * Staff-only, on-demand lookup — matches docs/architecture/kyc-flow.md's
     * "OPS->>FRAUD: Request risk assessment for customer" step. That step is triggered from the
     * (not-yet-built, Phase 15) Operations Portal, not from customer-kyc-service itself, so no
     * change was needed there for this phase — this endpoint exists so that future caller has
     * something real to call.
     */
    @GetMapping("/customers/{customerId}/risk-summary")
    @PreAuthorize("hasAnyRole('RISK_ANALYST','COMPLIANCE_OFFICER','ADMIN','AUDITOR')")
    public RiskSummaryResponse riskSummary(@PathVariable UUID customerId) {
        return RiskSummaryResponse.from(riskAssessmentService.summaryFor(customerId));
    }
}
