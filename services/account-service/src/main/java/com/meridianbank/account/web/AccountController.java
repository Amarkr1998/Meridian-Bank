package com.meridianbank.account.web;

import com.meridianbank.account.approval.ApprovalActionType;
import com.meridianbank.account.approval.ApprovalRequestResponse;
import com.meridianbank.account.approval.ApprovalService;
import com.meridianbank.account.client.LedgerServiceClient;
import com.meridianbank.account.domain.Account;
import com.meridianbank.account.domain.AccountStatus;
import com.meridianbank.account.security.JwtAuthenticationFilter;
import com.meridianbank.account.service.AccountService;
import com.meridianbank.account.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
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

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private static final Set<String> STAFF_ROLES =
            Set.of("ROLE_OPERATIONS", "ROLE_ADMIN", "ROLE_AUDITOR", "ROLE_COMPLIANCE_OFFICER");

    private final AccountService accountService;
    private final LedgerServiceClient ledgerServiceClient;
    private final ApprovalService approvalService;

    public AccountController(AccountService accountService, LedgerServiceClient ledgerServiceClient,
                              ApprovalService approvalService) {
        this.accountService = accountService;
        this.ledgerServiceClient = ledgerServiceClient;
        this.approvalService = approvalService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','AUDITOR') or " +
            "@resourceOwnership.isAccountOwner(#id, authentication.principal)")
    public AccountResponse get(@PathVariable UUID id, HttpServletRequest httpRequest) {
        Account account = accountService.getOrThrow(id);
        String bearerToken = (String) httpRequest.getAttribute(JwtAuthenticationFilter.BEARER_TOKEN_ATTRIBUTE);
        LedgerServiceClient.BalanceSummary balance = ledgerServiceClient.getBalance(id, bearerToken).orElse(null);
        return AccountResponse.from(account, balance);
    }

    @GetMapping
    public Page<AccountResponse> list(@RequestParam(required = false) UUID customerId,
                                       @RequestParam(required = false) AccountStatus status,
                                       Pageable pageable, Authentication authentication,
                                       @AuthenticationPrincipal UUID callerId) {
        boolean isStaff = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(STAFF_ROLES::contains);
        UUID effectiveCustomerId = isStaff ? customerId : callerId;
        return accountService.list(effectiveCustomerId, status, pageable).map(AccountResponse::from);
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','AUDITOR') or " +
            "@resourceOwnership.isAccountOwner(#id, authentication.principal)")
    public List<AccountStatusHistoryResponse> statusHistory(@PathVariable UUID id) {
        return accountService.getStatusHistory(id);
    }

    @PatchMapping("/{id}/freeze")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public AccountResponse freeze(@PathVariable UUID id, @Valid @RequestBody AccountStatusChangeRequest request,
                                   @AuthenticationPrincipal UUID actorId) {
        Account account = accountService.freeze(id, request.reason(), actorId);
        return AccountResponse.from(account);
    }

    @PatchMapping("/{id}/unfreeze")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public AccountResponse unfreeze(@PathVariable UUID id, @Valid @RequestBody AccountStatusChangeRequest request,
                                     @AuthenticationPrincipal UUID actorId) {
        Account account = accountService.unfreeze(id, request.reason(), actorId);
        return AccountResponse.from(account);
    }

    /**
     * Maker-checker gated (docs/adr/0011-maker-checker.md): this creates a PENDING_APPROVAL
     * request rather than blocking the account immediately. A different staff member must approve
     * it via {@code PATCH /api/v1/approvals/{id}/approve} before the block actually executes —
     * see ApprovalController.
     */
    @PatchMapping("/{id}/block")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApprovalRequestResponse block(@PathVariable UUID id, @Valid @RequestBody AccountStatusChangeRequest request,
                                          @AuthenticationPrincipal UUID actorId) {
        return ApprovalRequestResponse.from(
                approvalService.create(ApprovalActionType.BLOCK_ACCOUNT, id, request.reason(), actorId));
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public AccountResponse close(@PathVariable UUID id, @Valid @RequestBody AccountStatusChangeRequest request,
                                  @AuthenticationPrincipal UUID actorId) {
        Account account = accountService.close(id, request.reason(), actorId);
        return AccountResponse.from(account);
    }

    @PatchMapping("/{id}/limits")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public AccountResponse updateLimits(@PathVariable UUID id, @Valid @RequestBody UpdateAccountLimitsRequest request) {
        Account account = accountService.updateLimits(id, request.perTransactionLimit(), request.dailyLimit());
        return AccountResponse.from(account);
    }
}
