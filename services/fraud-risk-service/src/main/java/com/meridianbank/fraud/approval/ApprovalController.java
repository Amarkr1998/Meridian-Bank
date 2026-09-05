package com.meridianbank.fraud.approval;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * The checker-facing review queue for fraud-risk-service's maker-checker-gated actions — see
 * docs/architecture/maker-checker-flow.md. Read access mirrors the broader alert-viewing surface;
 * {@code approve}/{@code reject} additionally require the actionType-specific role — see
 * {@link ApprovalAuthorization}.
 */
@RestController
@RequestMapping("/api/v1/approvals")
@PreAuthorize("hasAnyRole('RISK_ANALYST','COMPLIANCE_OFFICER','ADMIN','AUDITOR')")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ApprovalRequestResponse> queue(@RequestParam(required = false) ApprovalStatus status,
                                                Pageable pageable) {
        return service.queue(status, pageable).map(ApprovalRequestResponse::from);
    }

    @GetMapping("/{id}")
    public ApprovalRequestResponse get(@PathVariable UUID id) {
        return ApprovalRequestResponse.from(service.getOrThrow(id));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("@approvalAuthorization.canDecide(#id, authentication)")
    public ApprovalRequestResponse approve(@PathVariable UUID id, @AuthenticationPrincipal UUID checkerId,
                                            @Valid @RequestBody(required = false) ApprovalDecisionRequest request) {
        return ApprovalRequestResponse.from(service.approve(id, checkerId, notes(request)));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("@approvalAuthorization.canDecide(#id, authentication)")
    public ApprovalRequestResponse reject(@PathVariable UUID id, @AuthenticationPrincipal UUID checkerId,
                                           @Valid @RequestBody(required = false) ApprovalDecisionRequest request) {
        return ApprovalRequestResponse.from(service.reject(id, checkerId, notes(request)));
    }

    private String notes(ApprovalDecisionRequest request) {
        return request != null ? request.notes() : null;
    }
}
