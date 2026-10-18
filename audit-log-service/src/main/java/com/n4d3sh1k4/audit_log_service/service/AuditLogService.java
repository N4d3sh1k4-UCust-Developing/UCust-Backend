package com.n4d3sh1k4.audit_log_service.service;

import com.n4d3sh1k4.audit_log_service.domain.model.AuditLogEntry;
import com.n4d3sh1k4.audit_log_service.domain.repository.AuditLogRepository;
import com.n4d3sh1k4.audit_log_service.dto.AuditLogEntryResponse;
import com.n4d3sh1k4.audit_log_service.dto.AuditLogPage;
import com.n4d3sh1k4.common.audit.AuditActorCategory;
import com.n4d3sh1k4.common.audit.AuditEvent;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Хранилище журнала действий. Запись идемпотентна по {@code eventId} —
 * повторная доставка сообщения из RabbitMQ не создаёт дубль.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void record(AuditEvent event) {
        if (event == null) {
            return;
        }
        if (event.eventId() != null && auditLogRepository.existsByEventId(event.eventId())) {
            return;
        }
        AuditLogEntry entry = AuditLogEntry.builder()
                .eventId(event.eventId() != null ? event.eventId() : UUID.randomUUID())
                .actorId(event.actorId())
                .actorCategory(event.actorCategory() != null ? event.actorCategory() : AuditActorCategory.USER)
                .action(event.action())
                .targetType(event.targetType())
                .targetId(event.targetId())
                .details(event.details())
                .ip(event.ip())
                .occurredAt(event.occurredAt() != null ? event.occurredAt() : Instant.now())
                .createdAt(Instant.now())
                .build();
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public AuditLogPage list(AuditActorCategory category, String action, UUID actorId,
                             Instant from, Instant to, int page, int limit) {
        int safePage = Math.max(page, 0);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeLimit,
                Sort.by(Sort.Direction.DESC, "occurredAt"));

        Specification<AuditLogEntry> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("actorCategory"), category));
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AuditLogEntry> result = auditLogRepository.findAll(specification, pageable);
        return new AuditLogPage(
                result.getContent().stream().map(AuditLogService::toResponse).toList(),
                result.getTotalElements(),
                safePage,
                safeLimit);
    }

    private static AuditLogEntryResponse toResponse(AuditLogEntry entry) {
        return new AuditLogEntryResponse(
                entry.getId(),
                entry.getEventId(),
                entry.getActorId(),
                entry.getActorCategory(),
                entry.getAction(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getDetails(),
                entry.getIp(),
                entry.getOccurredAt(),
                entry.getCreatedAt());
    }
}
