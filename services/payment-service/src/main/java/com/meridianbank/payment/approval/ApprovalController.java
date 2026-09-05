package com.meridianbank.payment.approval;

import com.meridianbank.payment.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** The checker-facing review queue for payment-service's maker-checker-gated action — see
 *  docs/architecture/maker-checker-flow.md. Staff-only throughout. */
@RestController
@RequestMapping("/api/v1/approvals")
@PreAuthorize("hasAnyRole('OPERATIONS','COMPLIANCE_OFFICER','RISK_ANALYST','ADMIN')")
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
    public ApprovalRequestResponse approve(@PathVariable UUID id, @AuthenticationPrincipal UUID checkerId,
                                            @Valid @RequestBody(required = false) ApprovalDecisionRequest request,
                                            HttpServletRequest httpRequest) {
        String bearerToken = (String) httpRequest.getAttribute(JwtAuthenticationFilter.BEARER_TOKEN_ATTRIBUTE);
        return ApprovalRequestResponse.from(service.approve(id, checkerId, notes(request), bearerToken));
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
