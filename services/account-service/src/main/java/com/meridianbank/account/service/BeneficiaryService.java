package com.meridianbank.account.service;

import com.meridianbank.account.audit.AuditAction;
import com.meridianbank.account.audit.AuditEventPublisher;
import com.meridianbank.account.config.BeneficiaryVerificationProperties;
import com.meridianbank.account.domain.*;
import com.meridianbank.account.exception.*;
import com.meridianbank.account.repository.AccountRepository;
import com.meridianbank.account.repository.BeneficiaryRepository;
import com.meridianbank.account.repository.BeneficiaryStatusHistoryRepository;
import com.meridianbank.account.web.dto.BeneficiaryStatusHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Beneficiary lifecycle: PENDING → ACTIVE (via OTP verification), ACTIVE ⇄ INACTIVE (self),
 * {PENDING|ACTIVE|INACTIVE} → BLOCKED (staff), BLOCKED → INACTIVE (staff unblock). See
 * account-service/README.md.
 */
@Service
public class BeneficiaryService {

    private static final Set<BeneficiaryStatus> BLOCK_FROM =
            EnumSet.of(BeneficiaryStatus.PENDING, BeneficiaryStatus.ACTIVE, BeneficiaryStatus.INACTIVE);

    private final BeneficiaryRepository beneficiaryRepository;
    private final BeneficiaryStatusHistoryRepository statusHistoryRepository;
    private final AccountRepository accountRepository;
    private final BeneficiaryVerificationService verificationService;
    private final BeneficiaryVerificationProperties verificationProperties;
    private final AuditEventPublisher auditEventPublisher;

    public BeneficiaryService(BeneficiaryRepository beneficiaryRepository,
                               BeneficiaryStatusHistoryRepository statusHistoryRepository,
                               AccountRepository accountRepository,
                               BeneficiaryVerificationService verificationService,
                               BeneficiaryVerificationProperties verificationProperties,
                               AuditEventPublisher auditEventPublisher) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.accountRepository = accountRepository;
        this.verificationService = verificationService;
        this.verificationProperties = verificationProperties;
        this.auditEventPublisher = auditEventPublisher;
    }

    public record AddResult(Beneficiary beneficiary, String devOtp, long expiresInSeconds) {
    }

    @Transactional
    public AddResult add(UUID customerId, String nickname, String beneficiaryName, String beneficiaryAccountNumber) {
        Account targetAccount = accountRepository.findByAccountNumber(beneficiaryAccountNumber)
                .orElseThrow(InvalidBeneficiaryAccountException::new);
        if (targetAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidBeneficiaryAccountException();
        }
        if (targetAccount.getCustomerId().equals(customerId)) {
            throw new SelfBeneficiaryNotAllowedException();
        }
        if (beneficiaryRepository.existsByCustomerIdAndBeneficiaryAccountNumber(customerId, beneficiaryAccountNumber)) {
            throw new DuplicateBeneficiaryException();
        }

        Beneficiary beneficiary = new Beneficiary(customerId, nickname, beneficiaryName, beneficiaryAccountNumber);
        beneficiaryRepository.save(beneficiary);

        BeneficiaryVerificationService.Challenge challenge = verificationService.createChallenge(beneficiary.getId());
        String devOtp = verificationProperties.demoExposeOtp() ? challenge.otp() : null;
        auditEventPublisher.record(AuditAction.BENEFICIARY_ADDED, "beneficiary", beneficiary.getId(), customerId,
                "CUSTOMER", "SUCCESS", "Added beneficiary " + nickname);
        return new AddResult(beneficiary, devOtp, challenge.expiresInSeconds());
    }

    @Transactional
    public Beneficiary verify(UUID beneficiaryId, String otp, UUID actorId) {
        Beneficiary beneficiary = getOrThrow(beneficiaryId);
        if (beneficiary.getStatus() != BeneficiaryStatus.PENDING) {
            throw new InvalidBeneficiaryStatusTransitionException(
                    "Only a PENDING beneficiary can be verified (current status: " + beneficiary.getStatus() + ")");
        }
        verificationService.verify(beneficiaryId, otp);
        transition(beneficiary, BeneficiaryStatus.ACTIVE, null, actorId);
        beneficiary.setActivatedAt(java.time.Instant.now());
        return beneficiaryRepository.save(beneficiary);
    }

    @Transactional
    public AddResult resendVerification(UUID beneficiaryId) {
        Beneficiary beneficiary = getOrThrow(beneficiaryId);
        if (beneficiary.getStatus() != BeneficiaryStatus.PENDING) {
            throw new InvalidBeneficiaryStatusTransitionException(
                    "Only a PENDING beneficiary needs verification (current status: " + beneficiary.getStatus() + ")");
        }
        BeneficiaryVerificationService.Challenge challenge = verificationService.createChallenge(beneficiaryId);
        String devOtp = verificationProperties.demoExposeOtp() ? challenge.otp() : null;
        return new AddResult(beneficiary, devOtp, challenge.expiresInSeconds());
    }

    @Transactional
    public Beneficiary activate(UUID beneficiaryId, UUID actorId) {
        Beneficiary beneficiary = getOrThrow(beneficiaryId);
        if (beneficiary.getStatus() != BeneficiaryStatus.INACTIVE) {
            throw new InvalidBeneficiaryStatusTransitionException(
                    "Only an INACTIVE beneficiary can be reactivated (current status: " + beneficiary.getStatus() + ")");
        }
        transition(beneficiary, BeneficiaryStatus.ACTIVE, null, actorId);
        return beneficiaryRepository.save(beneficiary);
    }

    @Transactional
    public Beneficiary deactivate(UUID beneficiaryId, UUID actorId) {
        Beneficiary beneficiary = getOrThrow(beneficiaryId);
        if (beneficiary.getStatus() != BeneficiaryStatus.ACTIVE) {
            throw new InvalidBeneficiaryStatusTransitionException(
                    "Only an ACTIVE beneficiary can be deactivated (current status: " + beneficiary.getStatus() + ")");
        }
        transition(beneficiary, BeneficiaryStatus.INACTIVE, null, actorId);
        return beneficiaryRepository.save(beneficiary);
    }

    @Transactional
    public Beneficiary block(UUID beneficiaryId, String reason, UUID actorId) {
        Beneficiary beneficiary = getOrThrow(beneficiaryId);
        if (!BLOCK_FROM.contains(beneficiary.getStatus())) {
            throw new InvalidBeneficiaryStatusTransitionException(
                    "Cannot block a beneficiary that is already " + beneficiary.getStatus());
        }
        transition(beneficiary, BeneficiaryStatus.BLOCKED, reason, actorId);
        return beneficiaryRepository.save(beneficiary);
    }

    @Transactional
    public Beneficiary unblock(UUID beneficiaryId, String reason, UUID actorId) {
        Beneficiary beneficiary = getOrThrow(beneficiaryId);
        if (beneficiary.getStatus() != BeneficiaryStatus.BLOCKED) {
            throw new InvalidBeneficiaryStatusTransitionException(
                    "Only a BLOCKED beneficiary can be unblocked (current status: " + beneficiary.getStatus() + ")");
        }
        transition(beneficiary, BeneficiaryStatus.INACTIVE, reason, actorId);
        return beneficiaryRepository.save(beneficiary);
    }

    @Transactional
    public void delete(UUID beneficiaryId) {
        Beneficiary beneficiary = getOrThrow(beneficiaryId);
        beneficiaryRepository.delete(beneficiary);
    }

    @Transactional(readOnly = true)
    public Beneficiary getOrThrow(UUID beneficiaryId) {
        return beneficiaryRepository.findById(beneficiaryId).orElseThrow(BeneficiaryNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<Beneficiary> list(UUID customerId, BeneficiaryStatus status, Pageable pageable) {
        if (customerId != null && status != null) {
            return beneficiaryRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        }
        if (customerId != null) {
            return beneficiaryRepository.findByCustomerId(customerId, pageable);
        }
        if (status != null) {
            return beneficiaryRepository.findByStatus(status, pageable);
        }
        return beneficiaryRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryStatusHistoryResponse> getStatusHistory(UUID beneficiaryId) {
        return statusHistoryRepository.findByBeneficiaryIdOrderByChangedAtDesc(beneficiaryId).stream()
                .map(BeneficiaryStatusHistoryResponse::from)
                .toList();
    }

    private void transition(Beneficiary beneficiary, BeneficiaryStatus target, String reason, UUID actorId) {
        BeneficiaryStatus previous = beneficiary.getStatus();
        beneficiary.setStatus(target);
        statusHistoryRepository.save(new BeneficiaryStatusHistory(beneficiary.getId(), previous, target, reason, actorId));
    }
}
