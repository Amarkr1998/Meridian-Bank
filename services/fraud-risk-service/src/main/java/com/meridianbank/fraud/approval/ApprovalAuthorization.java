package com.meridianbank.fraud.approval;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Referenced from {@code @PreAuthorize} SpEL ({@code @approvalAuthorization.canDecide(...)}) on
 * the generic checker endpoints, since the two gated action types need different checker role
 * sets — same restriction as their original (now maker-side) endpoints: fraud rule updates stay
 * COMPLIANCE_OFFICER/ADMIN only, alert resolution also allows RISK_ANALYST. Returns {@code true}
 * for a missing request — the controller's own not-found handling still applies; this check
 * exists to restrict who may decide, not to leak existence via an extra 403.
 */
@Component("approvalAuthorization")
public class ApprovalAuthorization {

    private static final Set<String> RULE_UPDATE_CHECKERS = Set.of("ROLE_COMPLIANCE_OFFICER", "ROLE_ADMIN");
    private static final Set<String> ALERT_RESOLUTION_CHECKERS =
            Set.of("ROLE_RISK_ANALYST", "ROLE_COMPLIANCE_OFFICER", "ROLE_ADMIN");

    private final ApprovalRequestRepository repository;

    public ApprovalAuthorization(ApprovalRequestRepository repository) {
        this.repository = repository;
    }

    public boolean canDecide(UUID approvalId, Authentication authentication) {
        return repository.findById(approvalId)
                .map(request -> {
                    Set<String> allowedRoles = request.getActionType() == ApprovalActionType.FRAUD_RULE_UPDATE
                            ? RULE_UPDATE_CHECKERS : ALERT_RESOLUTION_CHECKERS;
                    return authentication.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .anyMatch(allowedRoles::contains);
                })
                .orElse(true);
    }
}
