package com.meridianbank.kyc.web;

import com.meridianbank.kyc.domain.KycStatus;
import com.meridianbank.kyc.service.KycService;
import com.meridianbank.kyc.web.dto.KycRecordResponse;
import com.meridianbank.kyc.web.dto.RejectKycRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Compliance review queue — see docs/architecture/kyc-flow.md. */
@RestController
@RequestMapping("/api/v1/kyc")
@PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','ADMIN','AUDITOR','RISK_ANALYST')")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    @GetMapping
    public Page<KycRecordResponse> queue(@RequestParam(required = false) KycStatus status, Pageable pageable) {
        return kycService.queue(status, pageable);
    }

    @GetMapping("/{kycId}")
    public KycRecordResponse get(@PathVariable UUID kycId) {
        return kycService.toResponse(kycService.getOrThrow(kycId));
    }

    @PostMapping("/{kycId}/start-review")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','ADMIN')")
    public KycRecordResponse startReview(@PathVariable UUID kycId, @AuthenticationPrincipal UUID reviewerId) {
        return kycService.toResponse(kycService.startReview(kycId, reviewerId));
    }

    @PostMapping("/{kycId}/approve")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','ADMIN')")
    public KycRecordResponse approve(@PathVariable UUID kycId, @AuthenticationPrincipal UUID reviewerId) {
        return kycService.toResponse(kycService.approve(kycId, reviewerId));
    }

    @PostMapping("/{kycId}/reject")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','ADMIN')")
    public KycRecordResponse reject(@PathVariable UUID kycId, @AuthenticationPrincipal UUID reviewerId,
                                     @Valid @RequestBody RejectKycRequest request) {
        return kycService.toResponse(kycService.reject(kycId, reviewerId, request.reason()));
    }
}
