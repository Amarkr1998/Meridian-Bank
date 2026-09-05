package com.meridianbank.account.service;

import com.meridianbank.account.audit.AuditEventPublisher;
import com.meridianbank.account.config.BeneficiaryVerificationProperties;
import com.meridianbank.account.domain.*;
import com.meridianbank.account.exception.*;
import com.meridianbank.account.repository.AccountRepository;
import com.meridianbank.account.repository.BeneficiaryRepository;
import com.meridianbank.account.repository.BeneficiaryStatusHistoryRepository;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeneficiaryServiceTest {

    @Mock private BeneficiaryRepository beneficiaryRepository;
    @Mock private BeneficiaryStatusHistoryRepository statusHistoryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private BeneficiaryVerificationService verificationService;
    @Mock private AuditEventPublisher auditEventPublisher;

    private BeneficiaryService beneficiaryService;

    @BeforeEach
    void setUp() {
        BeneficiaryVerificationProperties properties = new BeneficiaryVerificationProperties(300, 5, true);
        beneficiaryService = new BeneficiaryService(beneficiaryRepository, statusHistoryRepository,
                accountRepository, verificationService, properties, auditEventPublisher);
    }

    private Account activeAccountFor(UUID ownerId, String accountNumber) {
        return new Account(ownerId, accountNumber, AccountType.SAVINGS, "USD",
                BigDecimal.TEN, BigDecimal.TEN);
    }

    @Test
    void add_withNonExistentAccountNumber_throws() {
        UUID customerId = UUID.randomUUID();
        when(accountRepository.findByAccountNumber("0000000000")).thenReturn(Optional.empty());

        assertThrows(InvalidBeneficiaryAccountException.class,
                () -> beneficiaryService.add(customerId, "Friend", "John Doe", "0000000000"));
    }

    @Test
    void add_withInactiveTargetAccount_throws() {
        UUID customerId = UUID.randomUUID();
        Account target = activeAccountFor(UUID.randomUUID(), "1112223334");
        target.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findByAccountNumber("1112223334")).thenReturn(Optional.of(target));

        assertThrows(InvalidBeneficiaryAccountException.class,
                () -> beneficiaryService.add(customerId, "Friend", "John Doe", "1112223334"));
    }

    @Test
    void add_targetingOwnAccount_throws() {
        UUID customerId = UUID.randomUUID();
        Account ownAccount = activeAccountFor(customerId, "5556667778");
        when(accountRepository.findByAccountNumber("5556667778")).thenReturn(Optional.of(ownAccount));

        assertThrows(SelfBeneficiaryNotAllowedException.class,
                () -> beneficiaryService.add(customerId, "Me", "Myself", "5556667778"));
    }

    @Test
    void add_duplicateBeneficiary_throws() {
        UUID customerId = UUID.randomUUID();
        Account target = activeAccountFor(UUID.randomUUID(), "9998887776");
        when(accountRepository.findByAccountNumber("9998887776")).thenReturn(Optional.of(target));
        when(beneficiaryRepository.existsByCustomerIdAndBeneficiaryAccountNumber(customerId, "9998887776"))
                .thenReturn(true);

        assertThrows(DuplicateBeneficiaryException.class,
                () -> beneficiaryService.add(customerId, "Friend", "John Doe", "9998887776"));
    }

    @Test
    void add_whenEligible_createsPendingBeneficiaryWithOtp() {
        UUID customerId = UUID.randomUUID();
        Account target = activeAccountFor(UUID.randomUUID(), "1231231231");
        when(accountRepository.findByAccountNumber("1231231231")).thenReturn(Optional.of(target));
        when(beneficiaryRepository.existsByCustomerIdAndBeneficiaryAccountNumber(any(), any())).thenReturn(false);
        when(beneficiaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(verificationService.createChallenge(any()))
                .thenReturn(new BeneficiaryVerificationService.Challenge("123456", 300));

        BeneficiaryService.AddResult result = beneficiaryService.add(customerId, "Friend", "John Doe", "1231231231");

        assertThat(result.beneficiary().getStatus()).isEqualTo(BeneficiaryStatus.PENDING);
        assertThat(result.devOtp()).isEqualTo("123456");
    }

    @Test
    void verify_onNonPendingBeneficiary_throwsWithoutCallingVerificationService() {
        Beneficiary beneficiary = new Beneficiary(UUID.randomUUID(), "Friend", "John Doe", "1231231231");
        beneficiary.setStatus(BeneficiaryStatus.ACTIVE);
        when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));

        assertThrows(InvalidBeneficiaryStatusTransitionException.class,
                () -> beneficiaryService.verify(beneficiary.getId(), "123456", UUID.randomUUID()));
    }

    @Test
    void verify_onPendingBeneficiary_activatesAndSetsActivatedAt() {
        Beneficiary beneficiary = new Beneficiary(UUID.randomUUID(), "Friend", "John Doe", "1231231231");
        when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));
        doNothing().when(verificationService).verify(beneficiary.getId(), "123456");
        when(beneficiaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Beneficiary verified = beneficiaryService.verify(beneficiary.getId(), "123456", UUID.randomUUID());

        assertThat(verified.getStatus()).isEqualTo(BeneficiaryStatus.ACTIVE);
        assertThat(verified.getActivatedAt()).isNotNull();
    }

    @Test
    void verify_withWrongOtp_propagatesExceptionWithoutActivating() {
        Beneficiary beneficiary = new Beneficiary(UUID.randomUUID(), "Friend", "John Doe", "1231231231");
        when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));
        doThrow(new InvalidBeneficiaryVerificationCodeException())
                .when(verificationService).verify(beneficiary.getId(), "000000");

        assertThrows(InvalidBeneficiaryVerificationCodeException.class,
                () -> beneficiaryService.verify(beneficiary.getId(), "000000", UUID.randomUUID()));
        assertThat(beneficiary.getStatus()).isEqualTo(BeneficiaryStatus.PENDING);
    }

    @Test
    void deactivate_fromPending_throws() {
        Beneficiary beneficiary = new Beneficiary(UUID.randomUUID(), "Friend", "John Doe", "1231231231");
        when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));

        assertThrows(InvalidBeneficiaryStatusTransitionException.class,
                () -> beneficiaryService.deactivate(beneficiary.getId(), UUID.randomUUID()));
    }

    @Test
    void block_thenUnblock_movesToInactive() {
        Beneficiary beneficiary = new Beneficiary(UUID.randomUUID(), "Friend", "John Doe", "1231231231");
        when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));
        when(beneficiaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Beneficiary blocked = beneficiaryService.block(beneficiary.getId(), "suspected fraud", UUID.randomUUID());
        assertThat(blocked.getStatus()).isEqualTo(BeneficiaryStatus.BLOCKED);

        Beneficiary unblocked = beneficiaryService.unblock(beneficiary.getId(), "cleared", UUID.randomUUID());
        assertThat(unblocked.getStatus()).isEqualTo(BeneficiaryStatus.INACTIVE);
    }
}
