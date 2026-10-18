package com.n4d3sh1k4.audit_log_service.domain.repository;

import com.n4d3sh1k4.audit_log_service.domain.model.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID>,
        JpaSpecificationExecutor<AuditLogEntry> {

    boolean existsByEventId(UUID eventId);
}
