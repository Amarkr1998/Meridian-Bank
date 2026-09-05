package com.meridianbank.account.security;

import com.meridianbank.account.repository.AccountOpeningRequestRepository;
import com.meridianbank.account.repository.AccountRepository;
import com.meridianbank.account.repository.BeneficiaryRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Referenced from {@code @PreAuthorize} SpEL (e.g. {@code @resourceOwnership.isAccountOwner(...)})
 * for endpoints where the path variable is the resource's own id rather than the customer id
 * directly, so a plain {@code #id == authentication.principal} comparison isn't possible. Returns
 * {@code false} (never throws) for a missing resource — the controller's own not-found handling
 * still applies once the request is allowed to proceed as staff, or a genuine owner gets a
 * consistent 404 rather than this check leaking existence via a 403.
 */
@Component("resourceOwnership")
public class ResourceOwnership {

    private final AccountOpeningRequestRepository requestRepository;
    private final AccountRepository accountRepository;
    private final BeneficiaryRepository beneficiaryRepository;

    public ResourceOwnership(AccountOpeningRequestRepository requestRepository, AccountRepository accountRepository,
                              BeneficiaryRepository beneficiaryRepository) {
        this.requestRepository = requestRepository;
        this.accountRepository = accountRepository;
        this.beneficiaryRepository = beneficiaryRepository;
    }

    public boolean isAccountRequestOwner(UUID requestId, UUID callerId) {
        return requestRepository.findById(requestId)
                .map(r -> r.getCustomerId().equals(callerId))
                .orElse(false);
    }

    public boolean isAccountOwner(UUID accountId, UUID callerId) {
        return accountRepository.findById(accountId)
                .map(a -> a.getCustomerId().equals(callerId))
                .orElse(false);
    }

    public boolean isBeneficiaryOwner(UUID beneficiaryId, UUID callerId) {
        return beneficiaryRepository.findById(beneficiaryId)
                .map(b -> b.getCustomerId().equals(callerId))
                .orElse(false);
    }
}
