package com.meridianbank.fraud.repository;

import com.meridianbank.fraud.domain.RiskAssessment;
import com.meridianbank.fraud.domain.RiskDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {

    /** HIGH_VELOCITY / RAPID_SEQUENTIAL_TRANSFERS (same query, different window). */
    long countByCustomerIdAndCreatedAtAfter(UUID customerId, Instant since);

    /** NEW_BENEFICIARY_HIGH_AMOUNT: has this customer ever transferred to this destination before? */
    boolean existsByCustomerIdAndDestinationAccountId(UUID customerId, UUID destinationAccountId);

    /** REPEAT_RISKY_BEHAVIOR. */
    long countByCustomerIdAndDecisionInAndCreatedAtAfter(UUID customerId, List<RiskDecision> decisions, Instant since);

    /** AML VELOCITY: sum of this customer's transaction amounts in the trailing window. */
    @Query("select coalesce(sum(r.amount), 0) from RiskAssessment r "
            + "where r.customerId = :customerId and r.createdAt > :since")
    BigDecimal sumAmountByCustomerIdSince(@Param("customerId") UUID customerId, @Param("since") Instant since);

    /** AML STRUCTURING: count of this customer's transactions clustered just under a threshold. */
    long countByCustomerIdAndAmountBetweenAndCreatedAtAfter(UUID customerId, BigDecimal low, BigDecimal high,
                                                             Instant since);

    /** AML REPEATED_NEW_BENEFICIARY: count of this customer's transfers to the same destination. */
    long countByCustomerIdAndDestinationAccountIdAndCreatedAtAfter(UUID customerId, UUID destinationAccountId,
                                                                    Instant since);

    List<RiskAssessment> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
