package com.n4d3sh1k4.audit_log_service.controller;

import com.n4d3sh1k4.audit_log_service.dto.AuditLogPage;
import com.n4d3sh1k4.audit_log_service.service.AuditLogService;
import com.n4d3sh1k4.common.audit.AuditActorCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Журнал действий (матрица ролей, разделы 28–32).
 * <ul>
 *   <li>Журнал действий Администраторов — только GEN_ADMIN.</li>
 *     <li>Журналы Модераторов, Тех. поддержки и Пользователей — GEN_ADMIN/ADMIN.</li>
 * </ul>
 */
@Tag(name = "Журнал действий", description = "Просмотр логов действий администраторов, модераторов, поддержки и пользователей")
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "Журнал действий администраторов", description = "Доступно только главному администратору (GEN_ADMIN).")
    @PreAuthorize("hasRole('GEN_ADMIN')")
    @GetMapping("/admins")
    public AuditLogPage admins(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return auditLogService.list(AuditActorCategory.ADMIN, action, actorId, from, to, page, limit);
    }

    @Operation(summary = "Журнал действий модераторов")
    @PreAuthorize("hasAnyRole('GEN_ADMIN','ADMIN')")
    @GetMapping("/moderators")
    public AuditLogPage moderators(
            @Parameter(description = "Фильтр по коду действия")
            @RequestParam(required = false) String action,
            @Parameter(description = "Фильтр по id актора")
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return auditLogService.list(AuditActorCategory.MODERATOR, action, actorId, from, to, page, limit);
    }

    @Operation(summary = "Журнал действий тех. поддержки")
    @PreAuthorize("hasAnyRole('GEN_ADMIN','ADMIN')")
    @GetMapping("/support")
    public AuditLogPage support(
            @Parameter(description = "Фильтр по коду действия")
            @RequestParam(required = false) String action,
            @Parameter(description = "Фильтр по id актора")
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return auditLogService.list(AuditActorCategory.SUPPORT, action, actorId, from, to, page, limit);
    }

    @Operation(summary = "Журнал действий пользователей")
    @PreAuthorize("hasAnyRole('GEN_ADMIN','ADMIN')")
    @GetMapping("/users")
    public AuditLogPage users(
            @Parameter(description = "Фильтр по коду действия")
            @RequestParam(required = false) String action,
            @Parameter(description = "Фильтр по id актора")
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return auditLogService.list(AuditActorCategory.USER, action, actorId, from, to, page, limit);
    }
}
