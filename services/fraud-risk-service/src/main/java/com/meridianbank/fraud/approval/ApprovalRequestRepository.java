package com.meridianbank.fraud.approval;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    Page<ApprovalRequest> findByStatus(ApprovalStatus status, Pageable pageable);

    boolean existsByResourceIdAndActionTypeAndStatus(UUID resourceId, ApprovalActionType actionType,
                                                       ApprovalStatus status);
}
