package com.meridianbank.notification.web;

import com.meridianbank.notification.service.NotificationQueryService;
import com.meridianbank.notification.web.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

/**
 * A customer's own notification inbox — self-service, same staff-can-also-filter-by-customerId
 * pattern used by payment-service's/customer-kyc-service's list endpoints. Ingestion happens
 * exclusively through the Kafka consumer (see NotificationEventListener) — there is no HTTP
 * create endpoint.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final Set<String> STAFF_ROLES =
            Set.of("ROLE_OPERATIONS", "ROLE_ADMIN", "ROLE_AUDITOR", "ROLE_COMPLIANCE_OFFICER");

    private final NotificationQueryService service;

    public NotificationController(NotificationQueryService service) {
        this.service = service;
    }

    @GetMapping
    public Page<NotificationResponse> list(@RequestParam(required = false) UUID customerId,
                                            @RequestParam(required = false) Boolean read, Pageable pageable,
                                            Authentication authentication, @AuthenticationPrincipal UUID callerId) {
        boolean isStaff = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(STAFF_ROLES::contains);
        UUID effectiveCustomerId = isStaff && customerId != null ? customerId : callerId;
        return service.listForCustomer(effectiveCustomerId, read, pageable).map(NotificationResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','AUDITOR','COMPLIANCE_OFFICER') "
            + "or @resourceOwnership.isNotificationOwner(#id, authentication.principal)")
    public NotificationResponse get(@PathVariable UUID id) {
        return NotificationResponse.from(service.getOrThrow(id));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("@resourceOwnership.isNotificationOwner(#id, authentication.principal)")
    public NotificationResponse markRead(@PathVariable UUID id) {
        return NotificationResponse.from(service.markRead(id));
    }
}
