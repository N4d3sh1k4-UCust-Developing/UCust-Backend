package com.n4d3sh1k4.audit_log_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Страница журнала действий")
public record AuditLogPage(
        @Schema(description = "Записи на текущей странице")
        List<AuditLogEntryResponse> entries,

        @Schema(description = "Общее количество", example = "42")
        long total,

        @Schema(description = "Номер страницы (0-based)", example = "0")
        int page,

        @Schema(description = "Размер страницы", example = "20")
        int limit) {}
