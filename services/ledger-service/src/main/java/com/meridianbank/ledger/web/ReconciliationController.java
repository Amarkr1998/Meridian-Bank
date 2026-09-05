package com.meridianbank.ledger.web;

import com.meridianbank.ledger.reconciliation.ReconciliationService;
import com.meridianbank.ledger.reconciliation.ReconciliationStatus;
import com.meridianbank.ledger.web.dto.ReconciliationRecordResponse;
import com.meridianbank.ledger.web.dto.ResolutionRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * The operations reconciliation queue — see docs/reconciliation/reconciliation-design.md's
 * "Dashboard" section (there is no actual Ops Portal yet, Phase 15 — this is the real backend API
 * a future one would call). Staff-only throughout; investigate/resolve is narrower than plain
 * viewing, matching the pattern used by every other staff review queue in this project (e.g.
 * fraud-risk-service's FraudAlertController).
 */
@RestController
@RequestMapping("/api/v1/ledger/reconciliation-records")
@PreAuthorize("hasAnyRole('OPERATIONS','ADMIN','AUDITOR','COMPLIANCE_OFFICER')")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    public Page<ReconciliationRecordResponse> queue(@RequestParam(required = false) ReconciliationStatus status,
                                                      Pageable pageable) {
        return reconciliationService.queue(status, pageable).map(ReconciliationRecordResponse::from);
    }

    @GetMapping("/{id}")
    public ReconciliationRecordResponse get(@PathVariable UUID id) {
        return ReconciliationRecordResponse.from(reconciliationService.getOrThrow(id));
    }

    @PatchMapping("/{id}/start-investigation")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public ReconciliationRecordResponse startInvestigation(@PathVariable UUID id,
                                                             @AuthenticationPrincipal UUID actorId) {
        return ReconciliationRecordResponse.from(reconciliationService.startInvestigation(id, actorId));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public ReconciliationRecordResponse resolve(@PathVariable UUID id, @AuthenticationPrincipal UUID actorId,
                                                 @Valid @RequestBody(required = false) ResolutionRequest request) {
        String notes = request != null ? request.notes() : null;
        return ReconciliationRecordResponse.from(reconciliationService.resolve(id, actorId, notes));
    }
}
