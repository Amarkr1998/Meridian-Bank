package com.meridianbank.notification.ingest;

import com.meridianbank.notification.domain.Notification;
import com.meridianbank.notification.domain.NotificationType;
import com.meridianbank.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationIngestServiceTest {

    @Mock private NotificationRepository repository;

    private NotificationIngestService service;

    @BeforeEach
    void setUp() {
        service = new NotificationIngestService(repository);
    }

    @Test
    void ingest_newEvent_savesANotification() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(false);

        service.ingest(eventId, customerId, NotificationType.PAYMENT_SUCCESS, "Payment completed", "body text");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).saveAndFlush(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getType()).isEqualTo(NotificationType.PAYMENT_SUCCESS);
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void ingest_alreadySeenEventId_isANoOp() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(true);

        service.ingest(eventId, UUID.randomUUID(), NotificationType.PAYMENT_SUCCESS, "t", "b");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void ingest_concurrentDuplicateInsert_isToleratedNotThrown() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        service.ingest(eventId, UUID.randomUUID(), NotificationType.PAYMENT_SUCCESS, "t", "b");
        // no exception propagates — the unique constraint race is treated as a successful no-op
    }
}
