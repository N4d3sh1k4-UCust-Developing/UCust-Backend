package com.n4d3sh1k4.audit_log_service.dto;

import com.n4d3sh1k4.common.audit.AuditActorCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Запись журнала действий")
public record AuditLogEntryResponse(

        @Schema(description = "Идентификатор записи")
        UUID id,

        @Schema(description = "Идентификатор исходного события")
        UUID eventId,

        @Schema(description = "Id актора действия")
        UUID actorId,

        @Schema(description = "Категория актора (раздел журнала)")
        AuditActorCategory actorCategory,

        @Schema(description = "Код действия", example = "USER_BLOCKED")
        String action,

        @Schema(description = "Тип объекта действия", example = "USER")
        String targetType,

        @Schema(description = "Идентификатор объекта действия")
        String targetId,

        @Schema(description = "Описание действия")
        String details,

    @Schema(description = "IP актора")
    String ip,

    @Schema(description = "Момент совершения действия")
    java.time.Instant occurredAt,

    @Schema(description = "Момент записи в журнал")
    java.time.Instant createdAt
) {}
