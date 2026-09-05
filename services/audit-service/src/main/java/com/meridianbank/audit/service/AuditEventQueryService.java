package com.meridianbank.audit.service;

import com.meridianbank.audit.domain.AuditEvent;
import com.meridianbank.audit.exception.AuditEventNotFoundException;
import com.meridianbank.audit.repository.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Read-only — see docs/adr/0012-audit-architecture.md. Ingestion lives entirely in AuditIngestService. */
@Service
@Transactional(readOnly = true)
public class AuditEventQueryService {

    private final AuditEventRepository repository;

    public AuditEventQueryService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public Page<AuditEvent> search(UUID actorId, String action, String resourceType, UUID resourceId,
                                    String producedBy, String correlationId, Pageable pageable) {
        return repository.search(actorId, action, resourceType, resourceId, producedBy, correlationId, pageable);
    }

    public AuditEvent getOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(AuditEventNotFoundException::new);
    }
}
