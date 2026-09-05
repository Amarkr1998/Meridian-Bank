package com.meridianbank.audit.web;

import com.meridianbank.audit.service.AuditEventQueryService;
import com.meridianbank.audit.web.dto.AuditEventResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Read-only query surface for the audit trail — see docs/adr/0012-audit-architecture.md. Restricted
 * to the governance/oversight roles per
 * docs/governance/governance-principles.md ("AUDITOR has read-only access to audit data"); there is
 * deliberately no endpoint anywhere in this service that creates, edits, or deletes a row — see
 * SecurityConfig and AuditEventRepository.
 */
@RestController
@RequestMapping("/api/v1/audit-events")
@PreAuthorize("hasAnyRole('AUDITOR','COMPLIANCE_OFFICER','ADMIN')")
public class AuditEventController {

    private final AuditEventQueryService service;

    public AuditEventController(AuditEventQueryService service) {
        this.service = service;
    }

    @GetMapping
    public Page<AuditEventResponse> search(@RequestParam(required = false) UUID actorId,
                                            @RequestParam(required = false) String action,
                                            @RequestParam(required = false) String resourceType,
                                            @RequestParam(required = false) UUID resourceId,
                                            @RequestParam(required = false) String producedBy,
                                            @RequestParam(required = false) String correlationId,
                                            Pageable pageable) {
        return service.search(actorId, action, resourceType, resourceId, producedBy, correlationId, pageable)
                .map(AuditEventResponse::from);
    }

    @GetMapping("/{id}")
    public AuditEventResponse get(@PathVariable UUID id) {
        return AuditEventResponse.from(service.getOrThrow(id));
    }
}
