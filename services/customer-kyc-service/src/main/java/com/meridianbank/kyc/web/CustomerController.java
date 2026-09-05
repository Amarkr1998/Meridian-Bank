package com.meridianbank.kyc.web;

import com.meridianbank.kyc.approval.ApprovalActionType;
import com.meridianbank.kyc.approval.ApprovalRequestResponse;
import com.meridianbank.kyc.approval.ApprovalService;
import com.meridianbank.kyc.domain.CustomerStatus;
import com.meridianbank.kyc.service.CustomerRegistrationService;
import com.meridianbank.kyc.service.CustomerService;
import com.meridianbank.kyc.service.KycService;
import com.meridianbank.kyc.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerRegistrationService registrationService;
    private final CustomerService customerService;
    private final KycService kycService;
    private final ApprovalService approvalService;

    public CustomerController(CustomerRegistrationService registrationService, CustomerService customerService,
                               KycService kycService, ApprovalService approvalService) {
        this.registrationService = registrationService;
        this.customerService = customerService;
        this.kycService = kycService;
        this.approvalService = approvalService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterCustomerResponse register(@Valid @RequestBody RegisterCustomerRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/{id}/verify-contact")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyContact(@PathVariable UUID id, @Valid @RequestBody VerifyContactRequest request) {
        customerService.verifyContact(id, request.otp());
    }

    @PostMapping("/{id}/resend-verification")
    public ResendVerificationResponse resendVerification(@PathVariable UUID id) {
        return customerService.resendVerification(id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("#id == authentication.principal or hasAnyRole('OPERATIONS','COMPLIANCE_OFFICER','ADMIN','AUDITOR')")
    public CustomerResponse get(@PathVariable UUID id) {
        return CustomerResponse.from(customerService.getOrThrow(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("#id == authentication.principal or hasAnyRole('OPERATIONS','ADMIN')")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        return CustomerResponse.from(customerService.updateProfile(id, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATIONS','COMPLIANCE_OFFICER','ADMIN','AUDITOR')")
    public Page<CustomerResponse> list(@RequestParam(required = false) CustomerStatus status, Pageable pageable) {
        return customerService.list(status, pageable).map(CustomerResponse::from);
    }

    /**
     * Maker-checker gated (docs/adr/0011-maker-checker.md): this creates a PENDING_APPROVAL
     * request rather than changing the customer's status immediately. A different staff member
     * must approve it via {@code PATCH /api/v1/approvals/{id}/approve} before the status change
     * actually executes — see ApprovalController.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApprovalRequestResponse updateStatus(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateCustomerStatusRequest request,
                                                 @AuthenticationPrincipal UUID actorId) {
        return ApprovalRequestResponse.from(approvalService.create(ApprovalActionType.CUSTOMER_STATUS_CHANGE, id,
                request.status(), request.reason(), actorId));
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("#id == authentication.principal or hasAnyRole('OPERATIONS','COMPLIANCE_OFFICER','ADMIN','AUDITOR')")
    public List<CustomerStatusHistoryResponse> statusHistory(@PathVariable UUID id) {
        return customerService.getStatusHistory(id);
    }

    @PostMapping("/{id}/kyc")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("#id == authentication.principal")
    public KycRecordResponse submitKyc(@PathVariable UUID id, @Valid @RequestBody SubmitKycRequest request) {
        return kycService.toResponse(kycService.submit(id, request));
    }

    @GetMapping("/{id}/kyc")
    @PreAuthorize("#id == authentication.principal or hasAnyRole('COMPLIANCE_OFFICER','ADMIN','AUDITOR','RISK_ANALYST')")
    public List<KycRecordResponse> kycHistory(@PathVariable UUID id) {
        return kycService.history(id);
    }
}
