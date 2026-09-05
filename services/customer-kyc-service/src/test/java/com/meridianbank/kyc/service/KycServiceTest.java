package com.meridianbank.kyc.service;

import com.meridianbank.kyc.audit.AuditEventPublisher;
import com.meridianbank.kyc.domain.Customer;
import com.meridianbank.kyc.domain.DocumentType;
import com.meridianbank.kyc.domain.KycRecord;
import com.meridianbank.kyc.domain.KycStatus;
import com.meridianbank.kyc.exception.ContactNotVerifiedException;
import com.meridianbank.kyc.exception.InvalidKycTransitionException;
import com.meridianbank.kyc.exception.KycAlreadyInProgressException;
import com.meridianbank.kyc.outbox.OutboxWriter;
import com.meridianbank.kyc.repository.CustomerDocumentRepository;
import com.meridianbank.kyc.repository.KycRecordRepository;
import com.meridianbank.kyc.web.dto.DocumentEntry;
import com.meridianbank.kyc.web.dto.SubmitKycRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

    @Mock private KycRecordRepository kycRecordRepository;
    @Mock private CustomerDocumentRepository documentRepository;
    @Mock private CustomerService customerService;
    @Mock private OutboxWriter outboxWriter;
    @Mock private AuditEventPublisher auditEventPublisher;

    private KycService kycService;

    @BeforeEach
    void setUp() {
        kycService = new KycService(kycRecordRepository, documentRepository, customerService, outboxWriter,
                auditEventPublisher);
    }

    private Customer verifiedCustomer(UUID id) {
        Customer customer = new Customer(id, "jane@meridianbank.local", "Jane", "Doe",
                LocalDate.of(1990, 1, 1), "+1-555-0100", "1 Demo St", null, "Demo City", "DS",
                "00000", "Testland");
        customer.setContactVerified(true);
        return customer;
    }

    private SubmitKycRequest sampleRequest() {
        return new SubmitKycRequest("Testland", "Engineer",
                List.of(new DocumentEntry(DocumentType.NATIONAL_ID, "REF-123")));
    }

    @Test
    void submit_withUnverifiedContact_throws() {
        UUID customerId = UUID.randomUUID();
        Customer customer = verifiedCustomer(customerId);
        customer.setContactVerified(false);
        when(customerService.getOrThrow(customerId)).thenReturn(customer);

        assertThrows(ContactNotVerifiedException.class, () -> kycService.submit(customerId, sampleRequest()));
    }

    @Test
    void submit_withExistingInProgressSubmission_throws() {
        UUID customerId = UUID.randomUUID();
        when(customerService.getOrThrow(customerId)).thenReturn(verifiedCustomer(customerId));
        when(kycRecordRepository.existsByCustomerIdAndStatusIn(eq(customerId), anyList())).thenReturn(true);

        assertThrows(KycAlreadyInProgressException.class, () -> kycService.submit(customerId, sampleRequest()));
    }

    @Test
    void submit_whenEligible_createsPendingRecordWithDocuments() {
        UUID customerId = UUID.randomUUID();
        when(customerService.getOrThrow(customerId)).thenReturn(verifiedCustomer(customerId));
        when(kycRecordRepository.existsByCustomerIdAndStatusIn(eq(customerId), anyList())).thenReturn(false);
        when(kycRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KycRecord record = kycService.submit(customerId, sampleRequest());

        assertThat(record.getStatus()).isEqualTo(KycStatus.KYC_PENDING);
        assertThat(record.getCustomerId()).isEqualTo(customerId);
        verify1DocumentSaved();
        org.mockito.Mockito.verify(outboxWriter).write(eq("kyc.updated"), eq("kyc_record"), any(), any());
    }

    private void verify1DocumentSaved() {
        org.mockito.Mockito.verify(documentRepository).save(argThat(d ->
                d.getDocumentType() == DocumentType.NATIONAL_ID && d.getDocumentReference().equals("REF-123")));
    }

    @Test
    void startReview_onNonPendingRecord_throws() {
        UUID kycId = UUID.randomUUID();
        KycRecord record = new KycRecord(UUID.randomUUID(), "Testland", "Engineer");
        record.startReview(UUID.randomUUID()); // now IN_REVIEW
        when(kycRecordRepository.findById(kycId)).thenReturn(Optional.of(record));

        assertThrows(InvalidKycTransitionException.class, () -> kycService.startReview(kycId, UUID.randomUUID()));
    }

    @Test
    void approve_onPendingRecord_throwsBecauseNotYetInReview() {
        UUID kycId = UUID.randomUUID();
        KycRecord record = new KycRecord(UUID.randomUUID(), "Testland", "Engineer"); // still PENDING
        when(kycRecordRepository.findById(kycId)).thenReturn(Optional.of(record));

        assertThrows(InvalidKycTransitionException.class, () -> kycService.approve(kycId, UUID.randomUUID()));
    }

    @Test
    void approve_onInReviewRecord_succeeds() {
        UUID kycId = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        KycRecord record = new KycRecord(UUID.randomUUID(), "Testland", "Engineer");
        record.startReview(reviewer);
        when(kycRecordRepository.findById(kycId)).thenReturn(Optional.of(record));
        when(kycRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KycRecord approved = kycService.approve(kycId, reviewer);

        assertThat(approved.getStatus()).isEqualTo(KycStatus.KYC_VERIFIED);
        assertThat(approved.getReviewedBy()).isEqualTo(reviewer);
        org.mockito.Mockito.verify(outboxWriter).write(eq("kyc.updated"), eq("kyc_record"), any(), any());
    }
}
