package com.meridianbank.payment.security;

import com.meridianbank.payment.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Referenced from {@code @PreAuthorize} SpEL ({@code @resourceOwnership.isTransactionOwner(...)})
 * since the path variable is the transaction's own id, not the customer id directly. Returns
 * {@code false} (never throws) for a missing resource — see account-service's identical pattern.
 */
@Component("resourceOwnership")
public class ResourceOwnership {

    private final TransactionRepository transactionRepository;

    public ResourceOwnership(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public boolean isTransactionOwner(UUID transactionId, UUID callerId) {
        return transactionRepository.findById(transactionId)
                .map(t -> t.getCustomerId().equals(callerId))
                .orElse(false);
    }
}
