package com.meridianbank.fraud.web;

import com.meridianbank.fraud.approval.ApprovalRequestResponse;
import com.meridianbank.fraud.approval.ApprovalService;
import com.meridianbank.fraud.service.FraudRuleService;
import com.meridianbank.fraud.web.dto.FraudRuleResponse;
import com.meridianbank.fraud.web.dto.UpdateFraudRuleRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Configuration surface for {@code fraud_rules} weights/thresholds — see
 * docs/governance/governance-principles.md ("Configuration Governance"). Read access for staff
 * generally; mutation restricted to COMPLIANCE_OFFICER/ADMIN (risk policy ownership). As of
 * Phase 10, an update is maker-checker gated (docs/adr/0011-maker-checker.md, "configuration
 * changes"): this creates a PENDING_APPROVAL request rather than changing the rule immediately; a
 * different COMPLIANCE_OFFICER/ADMIN must approve it via
 * {@code PATCH /api/v1/approvals/{id}/approve} — see ApprovalController.
 */
@RestController
@RequestMapping("/api/v1/fraud-rules")
@PreAuthorize("hasAnyRole('RISK_ANALYST','COMPLIANCE_OFFICER','ADMIN','AUDITOR')")
public class FraudRuleController {

    private final FraudRuleService service;
    private final ApprovalService approvalService;

    public FraudRuleController(FraudRuleService service, ApprovalService approvalService) {
        this.service = service;
        this.approvalService = approvalService;
    }

    @GetMapping
    public List<FraudRuleResponse> list() {
        return service.list().stream().map(FraudRuleResponse::from).toList();
    }

    @GetMapping("/{id}")
    public FraudRuleResponse get(@PathVariable UUID id) {
        return FraudRuleResponse.from(service.getOrThrow(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApprovalRequestResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateFraudRuleRequest request,
                                           @AuthenticationPrincipal UUID updatedBy) {
        return ApprovalRequestResponse.from(approvalService.requestRuleUpdate(id, request.weight(),
                request.thresholdNumeric(), request.thresholdWindowSeconds(), request.thresholdCount(),
                request.enabled(), updatedBy));
    }
}
