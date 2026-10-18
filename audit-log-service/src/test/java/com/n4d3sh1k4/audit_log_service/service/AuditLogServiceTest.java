package com.n4d3sh1k4.audit_log_service.service;

import com.n4d3sh1k4.audit_log_service.domain.model.AuditLogEntry;
import com.n4d3sh1k4.audit_log_service.domain.repository.AuditLogRepository;
import com.n4d3sh1k4.audit_log_service.dto.AuditLogPage;
import com.n4d3sh1k4.common.audit.AuditActorCategory;
import com.n4d3sh1k4.common.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    private static final UUID EVENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACTOR_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    private AuditEvent event() {
        return new AuditEvent(EVENT_ID, ACTOR_ID, AuditActorCategory.ADMIN, "USER_BLOCKED",
                "USER", "cccccccc-cccc-cccc-cccc-cccccccccccc", "Блокировка", "10.0.0.5",
                Instant.parse("2026-10-17T10:00:00Z"));
    }

    @Test
    void record_savesEntry() {
        when(auditLogRepository.existsByEventId(EVENT_ID)).thenReturn(false);

        auditLogService.record(event());

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLogEntry saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(EVENT_ID);
        assertThat(saved.getActorId()).isEqualTo(ACTOR_ID);
        assertThat(saved.getActorCategory()).isEqualTo(AuditActorCategory.ADMIN);
        assertThat(saved.getAction()).isEqualTo("USER_BLOCKED");
        assertThat(saved.getTargetType()).isEqualTo("USER");
        assertThat(saved.getIp()).isEqualTo("10.0.0.5");
        assertThat(saved.getOccurredAt()).isEqualTo(Instant.parse("2026-10-17T10:00:00Z"));
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void record_whenDuplicateEventId_skipsSave() {
        when(auditLogRepository.existsByEventId(EVENT_ID)).thenReturn(true);

        auditLogService.record(event());

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void record_whenNullEvent_doesNothing() {
        auditLogService.record(null);

        verify(auditLogRepository, never()).existsByEventId(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void record_whenOccurredAtAndCategoryMissing_fillsDefaults() {
        when(auditLogRepository.existsByEventId(EVENT_ID)).thenReturn(false);

        auditLogService.record(new AuditEvent(EVENT_ID, ACTOR_ID, null, "LOGIN", null, null, null, null, null));

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActorCategory()).isEqualTo(AuditActorCategory.USER);
        assertThat(captor.getValue().getOccurredAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_mapsPage() {
        AuditLogEntry entry = AuditLogEntry.builder()
                .id(UUID.randomUUID())
                .eventId(EVENT_ID)
                .actorId(ACTOR_ID)
                .actorCategory(AuditActorCategory.MODERATOR)
                .action("WARNING_ISSUED")
                .targetType("WARNING")
                .targetId("dddddddd-dddd-dddd-dddd-dddddddddddd")
                .occurredAt(Instant.parse("2026-10-17T10:00:00Z"))
                .createdAt(Instant.parse("2026-10-17T10:00:01Z"))
                .build();
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));

        AuditLogPage page = auditLogService.list(AuditActorCategory.MODERATOR, "WARNING_ISSUED", null,
                null, null, 0, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.entries()).hasSize(1);
        assertThat(page.entries().get(0).action()).isEqualTo("WARNING_ISSUED");
        assertThat(page.entries().get(0).actorCategory()).isEqualTo(AuditActorCategory.MODERATOR);
        assertThat(page.page()).isZero();
        assertThat(page.limit()).isEqualTo(20);
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_clampsLimitAndPage() {
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        AuditLogPage page = auditLogService.list(AuditActorCategory.USER, null, null, null, null, -3, 5000);

        assertThat(page.page()).isZero();
        assertThat(page.limit()).isEqualTo(100);
    }
}
