package com.meridianbank.notification.service;

import com.meridianbank.notification.domain.Notification;
import com.meridianbank.notification.exception.NotificationNotFoundException;
import com.meridianbank.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Read + mark-read only — ingestion lives entirely in NotificationIngestService (Kafka-only). */
@Service
public class NotificationQueryService {

    private final NotificationRepository repository;

    public NotificationQueryService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<Notification> listForCustomer(UUID customerId, Boolean read, Pageable pageable) {
        return read != null
                ? repository.findByCustomerIdAndRead(customerId, read, pageable)
                : repository.findByCustomerId(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public Notification getOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(NotificationNotFoundException::new);
    }

    @Transactional
    public Notification markRead(UUID id) {
        Notification notification = getOrThrow(id);
        notification.markRead();
        return repository.save(notification);
    }
}
