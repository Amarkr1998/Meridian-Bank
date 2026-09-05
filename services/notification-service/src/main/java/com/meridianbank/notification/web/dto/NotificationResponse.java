package com.meridianbank.notification.web.dto;

import com.meridianbank.notification.domain.Notification;
import com.meridianbank.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id, UUID customerId, NotificationType type, String title, String body, boolean read, Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getCustomerId(), n.getType(), n.getTitle(), n.getBody(),
                n.isRead(), n.getCreatedAt());
    }
}
