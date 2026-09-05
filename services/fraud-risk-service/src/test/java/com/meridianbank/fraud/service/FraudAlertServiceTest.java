package com.meridianbank.fraud.service;

import com.meridianbank.fraud.domain.FraudAlert;
import com.meridianbank.fraud.domain.FraudAlertStatus;
import com.meridianbank.fraud.domain.RiskDecision;
import com.meridianbank.fraud.exception.FraudAlertNotFoundException;
import com.meridianbank.fraud.exception.InvalidAlertTransitionException;
import com.meridianbank.fraud.outbox.OutboxWriter;
import com.meridianbank.fraud.repository.FraudAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudAlertServiceTest {

    @Mock private FraudAlertRepository repository;
    @Mock private OutboxWriter outboxWriter;

    private FraudAlertService service;

    @BeforeEach
    void setUp() {
        service = new FraudAlertService(repository, outboxWriter);
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private FraudAlert openAlert() {
        return new FraudAlert(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), "USD", 50, RiskDecision.REVIEW, "HIGH_AMOUNT");
    }

    @Test
    void getOrThrow_unknownId_throws() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(FraudAlertNotFoundException.class, () -> service.getOrThrow(id));
    }

    @Test
    void startReview_onOpenAlert_movesToUnderReview() {
        FraudAlert alert = openAlert();
        UUID id = alert.getId();
        when(repository.findById(id)).thenReturn(Optional.of(alert));
        UUID reviewer = UUID.randomUUID();

        FraudAlert result = service.startReview(id, reviewer);

        assertThat(result.getStatus()).isEqualTo(FraudAlertStatus.UNDER_REVIEW);
        assertThat(result.getReviewedBy()).isEqualTo(reviewer);
    }

    @Test
    void startReview_onNonOpenAlert_throws() {
        FraudAlert alert = openAlert();
        alert.startReview(UUID.randomUUID());
        UUID id = alert.getId();
        when(repository.findById(id)).thenReturn(Optional.of(alert));

        assertThrows(InvalidAlertTransitionException.class, () -> service.startReview(id, UUID.randomUUID()));
    }

    @Test
    void clear_onUnderReviewAlert_resolvesAndPublishesOutboxEvent() {
        FraudAlert alert = openAlert();
        alert.startReview(UUID.randomUUID());
        UUID id = alert.getId();
        when(repository.findById(id)).thenReturn(Optional.of(alert));

        FraudAlert result = service.clear(id, UUID.randomUUID(), "false positive");

        assertThat(result.getStatus()).isEqualTo(FraudAlertStatus.CLEARED);
        assertThat(result.getResolutionNotes()).isEqualTo("false positive");
        org.mockito.Mockito.verify(outboxWriter).write(
                org.mockito.ArgumentMatchers.eq("fraud.detected"), org.mockito.ArgumentMatchers.eq("fraud_alert"),
                any(), any());
    }

    @Test
    void confirm_onOpenAlert_throwsBecauseNotYetUnderReview() {
        FraudAlert alert = openAlert();
        UUID id = alert.getId();
        when(repository.findById(id)).thenReturn(Optional.of(alert));

        assertThrows(InvalidAlertTransitionException.class, () -> service.confirm(id, UUID.randomUUID(), null));
    }

    @Test
    void confirm_onUnderReviewAlert_movesToConfirmedFraud() {
        FraudAlert alert = openAlert();
        alert.startReview(UUID.randomUUID());
        UUID id = alert.getId();
        when(repository.findById(id)).thenReturn(Optional.of(alert));

        FraudAlert result = service.confirm(id, UUID.randomUUID(), "confirmed with customer");

        assertThat(result.getStatus()).isEqualTo(FraudAlertStatus.CONFIRMED_FRAUD);
    }

    @Test
    void escalate_onUnderReviewAlert_movesToEscalated() {
        FraudAlert alert = openAlert();
        alert.startReview(UUID.randomUUID());
        UUID id = alert.getId();
        when(repository.findById(id)).thenReturn(Optional.of(alert));

        FraudAlert result = service.escalate(id, UUID.randomUUID(), null);

        assertThat(result.getStatus()).isEqualTo(FraudAlertStatus.ESCALATED);
    }
}
