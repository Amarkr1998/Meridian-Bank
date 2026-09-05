package com.meridianbank.account.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code beneficiaryName} is self-declared by the customer, not verified against the recipient's
 * real KYC identity — see account-service/README.md for why (cross-service name lookup would
 * need service-to-service auth this project doesn't have yet). {@code beneficiaryAccountNumber}
 * IS verified: it must match an existing, ACTIVE account at add time — see BeneficiaryService.
 * {@code activatedAt} lets payment-service (Phase 6) later reason about how long a beneficiary
 * has been active before allowing a high-value transfer to it — see docs/architecture (Section 16
 * "appropriate controls before high-value transfers").
 */
@Entity
@Table(name = "beneficiaries")
public class Beneficiary {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "beneficiary_name", nullable = false)
    private String beneficiaryName;

    @Column(name = "beneficiary_account_number", nullable = false)
    private String beneficiaryAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BeneficiaryStatus status = BeneficiaryStatus.PENDING;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Beneficiary() {
    }

    public Beneficiary(UUID customerId, String nickname, String beneficiaryName, String beneficiaryAccountNumber) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.nickname = nickname;
        this.beneficiaryName = beneficiaryName;
        this.beneficiaryAccountNumber = beneficiaryAccountNumber;
        this.status = BeneficiaryStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public String getBeneficiaryAccountNumber() {
        return beneficiaryAccountNumber;
    }

    public BeneficiaryStatus getStatus() {
        return status;
    }

    public void setStatus(BeneficiaryStatus status) {
        this.status = status;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
