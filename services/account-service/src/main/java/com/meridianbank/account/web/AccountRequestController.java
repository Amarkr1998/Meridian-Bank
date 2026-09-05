package com.meridianbank.account.web;

import com.meridianbank.account.domain.AccountOpeningStatus;
import com.meridianbank.account.security.JwtAuthenticationFilter;
import com.meridianbank.account.service.AccountOpeningService;
import com.meridianbank.account.web.dto.AccountRequestResponse;
import com.meridianbank.account.web.dto.CreateAccountRequest;
import com.meridianbank.account.web.dto.RejectAccountRequestRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Account opening request workflow. See docs/architecture/onboarding-flow.md. */
@RestController
@RequestMapping("/api/v1/accounts/requests")
public class AccountRequestController {

    private final AccountOpeningService accountOpeningService;

    public AccountRequestController(AccountOpeningService accountOpeningService) {
        this.accountOpeningService = accountOpeningService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountRequestResponse submit(@Valid @RequestBody CreateAccountRequest request,
                                          @AuthenticationPrincipal UUID customerId,
                                          HttpServletRequest httpRequest) {
        String bearerToken = (String) httpRequest.getAttribute(JwtAuthenticationFilter.BEARER_TOKEN_ATTRIBUTE);
        return AccountRequestResponse.from(
                accountOpeningService.submit(customerId, request.accountType(), bearerToken));
    }

    @GetMapping("/mine")
    public List<AccountRequestResponse> mine(@AuthenticationPrincipal UUID customerId) {
        return accountOpeningService.historyForCustomer(customerId).stream()
                .map(AccountRequestResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','AUDITOR') or " +
            "@resourceOwnership.isAccountRequestOwner(#id, authentication.principal)")
    public AccountRequestResponse get(@PathVariable UUID id) {
        return AccountRequestResponse.from(accountOpeningService.getOrThrow(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','AUDITOR')")
    public Page<AccountRequestResponse> queue(@RequestParam(required = false) AccountOpeningStatus status,
                                               Pageable pageable) {
        return accountOpeningService.queue(status, pageable).map(AccountRequestResponse::from);
    }

    @PostMapping("/{id}/start-review")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public AccountRequestResponse startReview(@PathVariable UUID id, @AuthenticationPrincipal UUID reviewerId) {
        return AccountRequestResponse.from(accountOpeningService.startReview(id, reviewerId));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public AccountRequestResponse approve(@PathVariable UUID id, @AuthenticationPrincipal UUID reviewerId) {
        return AccountRequestResponse.from(accountOpeningService.approve(id, reviewerId));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public AccountRequestResponse reject(@PathVariable UUID id, @AuthenticationPrincipal UUID reviewerId,
                                          @Valid @RequestBody RejectAccountRequestRequest request) {
        return AccountRequestResponse.from(accountOpeningService.reject(id, reviewerId, request.reason()));
    }
}
