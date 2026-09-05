package com.meridianbank.account.web;

import com.meridianbank.account.domain.Account;
import com.meridianbank.account.domain.Beneficiary;
import com.meridianbank.account.domain.BeneficiaryStatus;
import com.meridianbank.account.repository.AccountRepository;
import com.meridianbank.account.service.BeneficiaryService;
import com.meridianbank.account.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Beneficiary management — see docs (Section 16: "a new beneficiary should have appropriate
 * controls before high-value transfers", enforced here via OTP verification and an
 * {@code activatedAt} timestamp payment-service consumes in Phase 6).
 */
@RestController
@RequestMapping("/api/v1/beneficiaries")
public class BeneficiaryController {

    private static final Set<String> STAFF_ROLES =
            Set.of("ROLE_OPERATIONS", "ROLE_ADMIN", "ROLE_AUDITOR", "ROLE_COMPLIANCE_OFFICER", "ROLE_RISK_ANALYST");

    private final BeneficiaryService beneficiaryService;
    private final AccountRepository accountRepository;

    public BeneficiaryController(BeneficiaryService beneficiaryService, AccountRepository accountRepository) {
        this.beneficiaryService = beneficiaryService;
        this.accountRepository = accountRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddBeneficiaryResponse add(@Valid @RequestBody AddBeneficiaryRequest request,
                                       @AuthenticationPrincipal UUID customerId) {
        BeneficiaryService.AddResult result = beneficiaryService.add(customerId, request.nickname(),
                request.beneficiaryName(), request.beneficiaryAccountNumber());
        return new AddBeneficiaryResponse(toResponse(result.beneficiary()), result.expiresInSeconds(), result.devOtp());
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("@resourceOwnership.isBeneficiaryOwner(#id, authentication.principal)")
    public BeneficiaryResponse verify(@PathVariable UUID id, @Valid @RequestBody VerifyBeneficiaryRequest request,
                                       @AuthenticationPrincipal UUID actorId) {
        return toResponse(beneficiaryService.verify(id, request.otp(), actorId));
    }

    @PostMapping("/{id}/resend-verification")
    @PreAuthorize("@resourceOwnership.isBeneficiaryOwner(#id, authentication.principal)")
    public ResendBeneficiaryVerificationResponse resendVerification(@PathVariable UUID id) {
        BeneficiaryService.AddResult result = beneficiaryService.resendVerification(id);
        return new ResendBeneficiaryVerificationResponse(result.expiresInSeconds(), result.devOtp());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','AUDITOR','COMPLIANCE_OFFICER','RISK_ANALYST') or " +
            "@resourceOwnership.isBeneficiaryOwner(#id, authentication.principal)")
    public BeneficiaryResponse get(@PathVariable UUID id) {
        return toResponse(beneficiaryService.getOrThrow(id));
    }

    @GetMapping
    public Page<BeneficiaryResponse> list(@RequestParam(required = false) UUID customerId,
                                           @RequestParam(required = false) BeneficiaryStatus status,
                                           Pageable pageable, Authentication authentication,
                                           @AuthenticationPrincipal UUID callerId) {
        boolean isStaff = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(STAFF_ROLES::contains);
        UUID effectiveCustomerId = isStaff ? customerId : callerId;
        return beneficiaryService.list(effectiveCustomerId, status, pageable).map(this::toResponse);
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','AUDITOR','COMPLIANCE_OFFICER','RISK_ANALYST') or " +
            "@resourceOwnership.isBeneficiaryOwner(#id, authentication.principal)")
    public List<BeneficiaryStatusHistoryResponse> statusHistory(@PathVariable UUID id) {
        return beneficiaryService.getStatusHistory(id);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("@resourceOwnership.isBeneficiaryOwner(#id, authentication.principal)")
    public BeneficiaryResponse activate(@PathVariable UUID id, @AuthenticationPrincipal UUID actorId) {
        return toResponse(beneficiaryService.activate(id, actorId));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@resourceOwnership.isBeneficiaryOwner(#id, authentication.principal)")
    public BeneficiaryResponse deactivate(@PathVariable UUID id, @AuthenticationPrincipal UUID actorId) {
        return toResponse(beneficiaryService.deactivate(id, actorId));
    }

    @PatchMapping("/{id}/block")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','COMPLIANCE_OFFICER','RISK_ANALYST')")
    public BeneficiaryResponse block(@PathVariable UUID id, @Valid @RequestBody BeneficiaryStatusChangeRequest request,
                                      @AuthenticationPrincipal UUID actorId) {
        return toResponse(beneficiaryService.block(id, request.reason(), actorId));
    }

    @PatchMapping("/{id}/unblock")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','COMPLIANCE_OFFICER','RISK_ANALYST')")
    public BeneficiaryResponse unblock(@PathVariable UUID id, @Valid @RequestBody BeneficiaryStatusChangeRequest request,
                                        @AuthenticationPrincipal UUID actorId) {
        return toResponse(beneficiaryService.unblock(id, request.reason(), actorId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN') or @resourceOwnership.isBeneficiaryOwner(#id, authentication.principal)")
    public void delete(@PathVariable UUID id) {
        beneficiaryService.delete(id);
    }

    private BeneficiaryResponse toResponse(Beneficiary beneficiary) {
        Account destinationAccount = accountRepository.findByAccountNumber(beneficiary.getBeneficiaryAccountNumber())
                .orElse(null);
        return BeneficiaryResponse.from(beneficiary, destinationAccount);
    }
}
