package com.meridianbank.kyc.service;

import com.meridianbank.kyc.audit.AuditAction;
import com.meridianbank.kyc.audit.AuditEventPublisher;
import com.meridianbank.kyc.domain.Customer;
import com.meridianbank.kyc.domain.CustomerDocument;
import com.meridianbank.kyc.domain.KycRecord;
import com.meridianbank.kyc.domain.KycStatus;
import com.meridianbank.kyc.exception.*;
import com.meridianbank.kyc.outbox.OutboxWriter;
import com.meridianbank.kyc.repository.CustomerDocumentRepository;
import com.meridianbank.kyc.repository.KycRecordRepository;
import com.meridianbank.kyc.web.dto.DocumentEntry;
import com.meridianbank.kyc.web.dto.DocumentResponse;
import com.meridianbank.kyc.web.dto.KycRecordResponse;
import com.meridianbank.kyc.web.dto.SubmitKycRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * KYC review workflow: KYC_PENDING -> KYC_IN_REVIEW -> KYC_VERIFIED / KYC_REJECTED. Records are
 * append-only per submission — see KycRecord and docs/architecture/kyc-flow.md. Automated risk
 * assessment (fraud-risk-service) is not wired in until Phase 9; for now every decision is made
 * directly by a COMPLIANCE_OFFICER.
 */
@Service
public class KycService {

    private final KycRecordRepository kycRecordRepository;
    private final CustomerDocumentRepository documentRepository;
    private final CustomerService customerService;
    private final OutboxWriter outboxWriter;
    private final AuditEventPublisher auditEventPublisher;

    public KycService(KycRecordRepository kycRecordRepository, CustomerDocumentRepository documentRepository,
                       CustomerService customerService, OutboxWriter outboxWriter,
                       AuditEventPublisher auditEventPublisher) {
        this.kycRecordRepository = kycRecordRepository;
        this.documentRepository = documentRepository;
        this.customerService = customerService;
        this.outboxWriter = outboxWriter;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public KycRecord submit(UUID customerId, SubmitKycRequest request) {
        Customer customer = customerService.getOrThrow(customerId);
        if (!customer.isContactVerified()) {
            throw new ContactNotVerifiedException();
        }
        if (kycRecordRepository.existsByCustomerIdAndStatusIn(customerId,
                List.of(KycStatus.KYC_PENDING, KycStatus.KYC_IN_REVIEW))) {
            throw new KycAlreadyInProgressException();
        }

        KycRecord record = new KycRecord(customerId, request.nationality(), request.occupation());
        kycRecordRepository.save(record);

        for (DocumentEntry entry : request.documents()) {
            documentRepository.save(new CustomerDocument(record.getId(), entry.documentType(),
                    entry.documentReference()));
        }
        publishKycUpdated(record, null);
        auditEventPublisher.record(AuditAction.KYC_SUBMITTED, "kyc_record", record.getId(), customerId, "CUSTOMER",
                "SUCCESS", null);
        return record;
    }

    @Transactional
    public KycRecord startReview(UUID kycId, UUID reviewerId) {
        KycRecord record = getOrThrow(kycId);
        if (record.getStatus() != KycStatus.KYC_PENDING) {
            throw new InvalidKycTransitionException(
                    "Only a PENDING submission can be claimed for review (current status: " + record.getStatus() + ")");
        }
        KycStatus previous = record.getStatus();
        record.startReview(reviewerId);
        KycRecord saved = kycRecordRepository.save(record);
        publishKycUpdated(saved, previous);
        return saved;
    }

    @Transactional
    public KycRecord approve(UUID kycId, UUID reviewerId) {
        KycRecord record = requireInReview(kycId);
        KycStatus previous = record.getStatus();
        record.approve(reviewerId);
        KycRecord saved = kycRecordRepository.save(record);
        publishKycUpdated(saved, previous);
        auditEventPublisher.record(AuditAction.KYC_APPROVED, "kyc_record", saved.getId(), reviewerId, null,
                "APPROVED", null);
        return saved;
    }

    @Transactional
    public KycRecord reject(UUID kycId, UUID reviewerId, String reason) {
        KycRecord record = requireInReview(kycId);
        KycStatus previous = record.getStatus();
        record.reject(reviewerId, reason);
        KycRecord saved = kycRecordRepository.save(record);
        publishKycUpdated(saved, previous);
        auditEventPublisher.record(AuditAction.KYC_REJECTED, "kyc_record", saved.getId(), reviewerId, null,
                "REJECTED", reason);
        return saved;
    }

    private void publishKycUpdated(KycRecord record, KycStatus previousStatus) {
        outboxWriter.write("kyc.updated", "kyc_record", record.getId(),
                new KycUpdatedPayload(record.getId(), record.getCustomerId(), record.getStatus(), previousStatus));
    }

    private record KycUpdatedPayload(UUID kycId, UUID customerId, KycStatus status, KycStatus previousStatus) {
    }

    private KycRecord requireInReview(UUID kycId) {
        KycRecord record = getOrThrow(kycId);
        if (record.getStatus() != KycStatus.KYC_IN_REVIEW) {
            throw new InvalidKycTransitionException(
                    "Only a submission IN_REVIEW can be approved or rejected (current status: " + record.getStatus() + ")");
        }
        return record;
    }

    @Transactional(readOnly = true)
    public KycRecord getOrThrow(UUID kycId) {
        return kycRecordRepository.findById(kycId).orElseThrow(KycRecordNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<KycRecordResponse> history(UUID customerId) {
        return kycRecordRepository.findByCustomerIdOrderBySubmittedAtDesc(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<KycRecordResponse> queue(KycStatus status, Pageable pageable) {
        return kycRecordRepository.findByStatus(status, pageable).map(this::toResponse);
    }

    public KycRecordResponse toResponse(KycRecord record) {
        List<DocumentResponse> documents = documentRepository.findByKycRecordId(record.getId()).stream()
                .map(DocumentResponse::from)
                .toList();
        return KycRecordResponse.from(record, documents);
    }
}
