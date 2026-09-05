package com.meridianbank.kyc.approval;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** The checker-facing review queue for customer-kyc-service's maker-checker-gated actions — see
 *  docs/architecture/maker-checker-flow.md. Staff-only throughout. */
@RestController
@RequestMapping("/api/v1/approvals")
@PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','AUDITOR')")
    public Page<ApprovalRequestResponse> queue(@RequestParam(required = false) ApprovalStatus status,
                                                Pageable pageable) {
        return service.queue(status, pageable).map(ApprovalRequestResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','AUDITOR')")
    public ApprovalRequestResponse get(@PathVariable UUID id) {
        return ApprovalRequestResponse.from(service.getOrThrow(id));
    }

    @PatchMapping("/{id}/approve")
    public ApprovalRequestResponse approve(@PathVariable UUID id, @AuthenticationPrincipal UUID checkerId,
                                            @Valid @RequestBody(required = false) ApprovalDecisionRequest request) {
        return ApprovalRequestResponse.from(service.approve(id, checkerId, notes(request)));
    }

    @PatchMapping("/{id}/reject")
    public ApprovalRequestResponse reject(@PathVariable UUID id, @AuthenticationPrincipal UUID checkerId,
                                           @Valid @RequestBody(required = false) ApprovalDecisionRequest request) {
        return ApprovalRequestResponse.from(service.reject(id, checkerId, notes(request)));
    }

    private String notes(ApprovalDecisionRequest request) {
        return request != null ? request.notes() : null;
    }
}
