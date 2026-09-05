package com.meridianbank.kyc.security;

import com.meridianbank.kyc.support.SupportRequestRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Referenced from {@code @PreAuthorize} SpEL ({@code @resourceOwnership.isSupportRequestOwner(...)})
 * since the path variable is the support request's own id, not the customer id directly. Returns
 * {@code false} (never throws) for a missing resource — see account-service/payment-service's
 * identical pattern.
 */
@Component("resourceOwnership")
public class ResourceOwnership {

    private final SupportRequestRepository supportRequestRepository;

    public ResourceOwnership(SupportRequestRepository supportRequestRepository) {
        this.supportRequestRepository = supportRequestRepository;
    }

    public boolean isSupportRequestOwner(UUID supportRequestId, UUID callerId) {
        return supportRequestRepository.findById(supportRequestId)
                .map(r -> r.getCustomerId().equals(callerId))
                .orElse(false);
    }
}
