package com.meridianbank.fraud.web;

import com.meridianbank.fraud.domain.AmlAlertStatus;
import com.meridianbank.fraud.service.AmlAlertService;
import com.meridianbank.fraud.web.dto.AmlAlertResponse;
import com.meridianbank.fraud.web.dto.ResolutionRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Only COMPLIANCE_OFFICER/ADMIN may transition an AML alert; RISK_ANALYST/AUDITOR may view but
 * not clear/escalate — see docs/architecture/aml-flow.md's Roles section (a narrower RBAC surface
 * than fraud alerts, deliberately: AML is a compliance function, not a risk-analyst one).
 */
@RestController
@RequestMapping("/api/v1/aml-alerts")
@PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','ADMIN')")
public class AmlAlertController {

    private final AmlAlertService service;

    public AmlAlertController(AmlAlertService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RISK_ANALYST','COMPLIANCE_OFFICER','ADMIN','AUDITOR')")
    public Page<AmlAlertResponse> queue(@RequestParam(required = false) AmlAlertStatus status,
                                         @RequestParam(required = false) UUID customerId, Pageable pageable) {
        return service.queue(status, customerId, pageable).map(AmlAlertResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RISK_ANALYST','COMPLIANCE_OFFICER','ADMIN','AUDITOR')")
    public AmlAlertResponse get(@PathVariable UUID id) {
        return AmlAlertResponse.from(service.getOrThrow(id));
    }

    @PatchMapping("/{id}/start-review")
    public AmlAlertResponse startReview(@PathVariable UUID id, @AuthenticationPrincipal UUID reviewerId) {
        return AmlAlertResponse.from(service.startReview(id, reviewerId));
    }

    @PatchMapping("/{id}/clear")
    public AmlAlertResponse clear(@PathVariable UUID id, @AuthenticationPrincipal UUID reviewerId,
                                   @Valid @RequestBody(required = false) ResolutionRequest request) {
        return AmlAlertResponse.from(service.clear(id, reviewerId, notes(request)));
    }

    @PatchMapping("/{id}/escalate")
    public AmlAlertResponse escalate(@PathVariable UUID id, @AuthenticationPrincipal UUID reviewerId,
                                      @Valid @RequestBody(required = false) ResolutionRequest request) {
        return AmlAlertResponse.from(service.escalate(id, reviewerId, notes(request)));
    }

    private String notes(ResolutionRequest request) {
        return request != null ? request.notes() : null;
    }
}
