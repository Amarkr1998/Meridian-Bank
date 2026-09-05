package com.meridianbank.kyc.web;

import com.meridianbank.kyc.support.SupportRequestService;
import com.meridianbank.kyc.support.SupportRequestStatus;
import com.meridianbank.kyc.web.dto.CreateSupportRequestRequest;
import com.meridianbank.kyc.web.dto.ResolveSupportRequestRequest;
import com.meridianbank.kyc.web.dto.SupportRequestResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

/**
 * Customer support tickets — see docs/database/domain-model.md and
 * SupportRequestService's Javadoc for why this lives in customer-kyc-service. Self-service for
 * customers (their own tickets only); a general staff review queue for OPERATIONS/ADMIN, viewable
 * (read-only) by the wider governance/oversight audience too.
 */
@RestController
@RequestMapping("/api/v1/support-requests")
public class SupportRequestController {

    private static final Set<String> STAFF_ROLES =
            Set.of("ROLE_OPERATIONS", "ROLE_ADMIN", "ROLE_AUDITOR", "ROLE_COMPLIANCE_OFFICER");

    private final SupportRequestService service;

    public SupportRequestController(SupportRequestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public SupportRequestResponse create(@Valid @RequestBody CreateSupportRequestRequest request,
                                          @AuthenticationPrincipal UUID customerId) {
        return SupportRequestResponse.from(
                service.create(customerId, request.category(), request.subject(), request.description()));
    }

    @GetMapping
    public Page<SupportRequestResponse> list(@RequestParam(required = false) UUID customerId,
                                              @RequestParam(required = false) SupportRequestStatus status,
                                              Pageable pageable, Authentication authentication,
                                              @AuthenticationPrincipal UUID callerId) {
        boolean isStaff = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(STAFF_ROLES::contains);
        if (!isStaff) {
            return service.listForCustomer(callerId, status, pageable).map(SupportRequestResponse::from);
        }
        return customerId != null
                ? service.listForCustomer(customerId, status, pageable).map(SupportRequestResponse::from)
                : service.queue(status, pageable).map(SupportRequestResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','AUDITOR','COMPLIANCE_OFFICER') "
            + "or @resourceOwnership.isSupportRequestOwner(#id, authentication.principal)")
    public SupportRequestResponse get(@PathVariable UUID id) {
        return SupportRequestResponse.from(service.getOrThrow(id));
    }

    @PatchMapping("/{id}/start-progress")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public SupportRequestResponse startProgress(@PathVariable UUID id, @AuthenticationPrincipal UUID staffId) {
        return SupportRequestResponse.from(service.startProgress(id, staffId));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public SupportRequestResponse resolve(@PathVariable UUID id, @AuthenticationPrincipal UUID staffId,
                                           @Valid @RequestBody ResolveSupportRequestRequest request) {
        return SupportRequestResponse.from(service.resolve(id, staffId, request.notes()));
    }
}
