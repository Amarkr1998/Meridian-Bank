package com.meridianbank.notification.security;

import com.meridianbank.notification.repository.NotificationRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Referenced from {@code @PreAuthorize} SpEL ({@code @resourceOwnership.isNotificationOwner(...)})
 * since the path variable is the notification's own id, not the customer id directly. Returns
 * {@code false} (never throws) for a missing resource — see account-service/payment-service's
 * identical pattern.
 */
@Component("resourceOwnership")
public class ResourceOwnership {

    private final NotificationRepository notificationRepository;

    public ResourceOwnership(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public boolean isNotificationOwner(UUID notificationId, UUID callerId) {
        return notificationRepository.findById(notificationId)
                .map(n -> n.getCustomerId().equals(callerId))
                .orElse(false);
    }
}
