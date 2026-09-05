package com.meridianbank.audit.repository;

import com.meridianbank.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Insert and read only — see docs/adr/0012-audit-architecture.md. There is intentionally no
 * {@code save}-overwrite-relevant update helper, no delete method, and nothing in this interface
 * (or anywhere else in this service) can mutate a row once inserted.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    boolean existsByEventId(UUID eventId);

    /**
     * Every filter is optional (the {@code :x IS NULL OR field = :x} pattern) so the operations
     * audit UI (docs/adr/0012-audit-architecture.md's {@code /ops/audit}) can query by any
     * combination without a combinatorial explosion of derived-query methods.
     */
    @Query("""
            SELECT e FROM AuditEvent e
            WHERE (:actorId IS NULL OR e.actorId = :actorId)
              AND (:action IS NULL OR e.action = :action)
              AND (:resourceType IS NULL OR e.resourceType = :resourceType)
              AND (:resourceId IS NULL OR e.resourceId = :resourceId)
              AND (:producedBy IS NULL OR e.producedBy = :producedBy)
              AND (:correlationId IS NULL OR e.correlationId = :correlationId)
            ORDER BY e.occurredAt DESC
            """)
    Page<AuditEvent> search(@Param("actorId") UUID actorId, @Param("action") String action,
                             @Param("resourceType") String resourceType, @Param("resourceId") UUID resourceId,
                             @Param("producedBy") String producedBy, @Param("correlationId") String correlationId,
                             Pageable pageable);
}
