package com.meridianbank.fraud.web;

import com.meridianbank.fraud.approval.ApprovalRequestResponse;
import com.meridianbank.fraud.approval.ApprovalService;
import com.meridianbank.fraud.domain.FraudAlertStatus;
import com.meridianbank.fraud.service.FraudAlertService;
import com.meridianbank.fraud.web.dto.FraudAlertResponse;
import com.meridianbank.fraud.web.dto.ResolutionRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Only RISK_ANALYST/COMPLIANCE_OFFICER/ADMIN may review, escalate, block, or clear a fraud alert
 * — see docs/architecture/fraud-flow.md's Roles section. As of Phase 10, the terminal resolution
 * (clear/escalate/confirm) is maker-checker gated (docs/adr/0011-maker-checker.md, "high-risk
 * fraud decisions") — these endpoints create a PENDING_APPROVAL request rather than resolving the
 * alert immediately; a different staff member must approve it via
 * {@code PATCH /api/v1/approvals/{id}/approve} — see ApprovalController. Claiming an alert for
 * review ({@code start-review}) remains single-actor — it doesn't decide anything yet.
 */
@RestController
@RequestMapping("/api/v1/fraud-alerts")
@PreAuthorize("hasAnyRole('RISK_ANALYST','COMPLIANCE_OFFICER','ADMIN')")
public class FraudAlertController {

    private final FraudAlertService service;
    private final ApprovalService approvalService;

    public FraudAlertController(FraudAlertService service, ApprovalService approvalService) {
        this.service = service;
        this.approvalService = approvalService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RISK_ANALYST','COMPLIANCE_OFFICER','ADMIN','AUDITOR')")
    public Page<FraudAlertResponse> queue(@RequestParam(required = false) FraudAlertStatus status,
                                           @RequestParam(required = false) UUID customerId, Pageable pageable) {
        return service.queue(status, customerId, pageable).map(FraudAlertResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RISK_ANALYST','COMPLIANCE_OFFICER','ADMIN','AUDITOR')")
    public FraudAlertResponse get(@PathVariable UUID id) {
        return FraudAlertResponse.from(service.getOrThrow(id));
    }

    @PatchMapping("/{id}/start-review")
    public FraudAlertResponse startReview(@PathVariable UUID id, @AuthenticationPrincipal UUID reviewerId) {
        return FraudAlertResponse.from(service.startReview(id, reviewerId));
    }

    @PatchMapping("/{id}/clear")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApprovalRequestResponse clear(@PathVariable UUID id, @AuthenticationPrincipal UUID reviewerId,
                                          @Valid @RequestBody(required = false) ResolutionRequest request) {
        return ApprovalRequestResponse.from(
                approvalService.requestAlertResolution(id, FraudAlertStatus.CLEARED, notes(request), reviewerId));
    }

    @PatchMapping("/{id}/escalate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApprovalRequestResponse escalate(@PathVariable UUID id, @AuthenticationPrincipal UUID reviewerId,
                                             @Valid @RequestBody(required = false) ResolutionRequest request) {
        return ApprovalRequestResponse.from(
                approvalService.requestAlertResolution(id, FraudAlertStatus.ESCALATED, notes(request), reviewerId));
    }

    @PatchMapping("/{id}/confirm")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApprovalRequestResponse confirm(@PathVariable UUID id, @AuthenticationPrincipal UUID reviewerId,
                                            @Valid @RequestBody(required = false) ResolutionRequest request) {
        return ApprovalRequestResponse.from(approvalService.requestAlertResolution(id,
                FraudAlertStatus.CONFIRMED_FRAUD, notes(request), reviewerId));
    }

    private String notes(ResolutionRequest request) {
        return request != null ? request.notes() : null;
    }
}
