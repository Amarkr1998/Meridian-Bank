package com.meridianbank.payment.web;

import com.meridianbank.payment.approval.ApprovalRequestResponse;
import com.meridianbank.payment.approval.ApprovalService;
import com.meridianbank.payment.approval.RequestReleaseRequest;
import com.meridianbank.payment.domain.TransactionStatus;
import com.meridianbank.payment.security.JwtAuthenticationFilter;
import com.meridianbank.payment.service.PaymentService;
import com.meridianbank.payment.web.dto.CreatePaymentRequest;
import com.meridianbank.payment.web.dto.PaymentResponse;
import com.meridianbank.payment.web.dto.TransactionStatusHistoryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Account-to-account transfers. See docs/architecture/payment-flow.md and
 * payment-service/README.md for what is (and, honestly, is not) implemented in this phase.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Validated
public class PaymentController {

    private static final Set<String> STAFF_ROLES =
            Set.of("ROLE_OPERATIONS", "ROLE_ADMIN", "ROLE_AUDITOR", "ROLE_COMPLIANCE_OFFICER", "ROLE_RISK_ANALYST");

    private final PaymentService paymentService;
    private final ApprovalService approvalService;

    public PaymentController(PaymentService paymentService, ApprovalService approvalService) {
        this.paymentService = paymentService;
        this.approvalService = approvalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request,
                                   @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
                                   @AuthenticationPrincipal UUID customerId,
                                   HttpServletRequest httpRequest) {
        String bearerToken = (String) httpRequest.getAttribute(JwtAuthenticationFilter.BEARER_TOKEN_ATTRIBUTE);
        return paymentService.createPayment(customerId, request, idempotencyKey, bearerToken);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','AUDITOR','COMPLIANCE_OFFICER','RISK_ANALYST') or " +
            "@resourceOwnership.isTransactionOwner(#id, authentication.principal)")
    public PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.from(paymentService.getOrThrow(id));
    }

    @GetMapping
    public Page<PaymentResponse> list(@RequestParam(required = false) UUID customerId,
                                       @RequestParam(required = false) TransactionStatus status,
                                       Pageable pageable, Authentication authentication,
                                       @AuthenticationPrincipal UUID callerId) {
        boolean isStaff = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(STAFF_ROLES::contains);
        UUID effectiveCustomerId = isStaff ? customerId : callerId;
        return paymentService.list(effectiveCustomerId, status, pageable).map(PaymentResponse::from);
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','AUDITOR','COMPLIANCE_OFFICER','RISK_ANALYST') or " +
            "@resourceOwnership.isTransactionOwner(#id, authentication.principal)")
    public List<TransactionStatusHistoryResponse> statusHistory(@PathVariable UUID id) {
        return paymentService.getStatusHistory(id);
    }

    /**
     * Maker-checker gated (docs/adr/0011-maker-checker.md, "high-value transaction approval"):
     * requests release of a transaction that FAILED with FRAUD_REVIEW_REQUIRED — this does NOT
     * move any money by itself. A different staff member must approve it via
     * {@code PATCH /api/v1/approvals/{id}/approve} before a brand-new transaction is created and
     * actually processed — see ApprovalController and PaymentService#releaseHeldPayment.
     */
    @PostMapping("/{id}/request-release")
    @PreAuthorize("hasAnyRole('OPERATIONS','COMPLIANCE_OFFICER','RISK_ANALYST','ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApprovalRequestResponse requestRelease(@PathVariable UUID id,
                                                    @Valid @RequestBody RequestReleaseRequest request,
                                                    @AuthenticationPrincipal UUID requestedBy) {
        return ApprovalRequestResponse.from(approvalService.create(id, request.reason(), requestedBy));
    }
}
