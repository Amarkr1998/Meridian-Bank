package com.meridianbank.notification.repository;

import com.meridianbank.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByEventId(UUID eventId);

    Page<Notification> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Notification> findByCustomerIdAndRead(UUID customerId, boolean read, Pageable pageable);
}
