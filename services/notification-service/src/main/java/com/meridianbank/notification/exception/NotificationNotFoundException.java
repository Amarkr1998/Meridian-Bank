package com.meridianbank.notification.exception;

import org.springframework.http.HttpStatus;

public class NotificationNotFoundException extends NotificationServiceException {

    public NotificationNotFoundException() {
        super("NOTIFICATION_NOT_FOUND", HttpStatus.NOT_FOUND, "Notification not found");
    }
}
