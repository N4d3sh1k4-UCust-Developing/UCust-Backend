package com.n4d3sh1k4.audit_log_service.controller;

import com.n4d3sh1k4.audit_log_service.dto.AuditLogEntryResponse;
import com.n4d3sh1k4.audit_log_service.dto.AuditLogPage;
import com.n4d3sh1k4.audit_log_service.service.AuditLogService;
import com.n4d3sh1k4.common.audit.AuditActorCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.autoconfigure.web.DataWebAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(excludeAutoConfiguration = DataWebAutoConfiguration.class,
        properties = "app.test.webmvc-config=enabled")
@Import(AdminAuditLogController.class)
@AutoConfigureMockMvc(addFilters = true)
class AdminAuditLogControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    private AuditLogPage page() {
        return new AuditLogPage(
                List.of(new AuditLogEntryResponse(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        ACTOR_ID,
                        AuditActorCategory.ADMIN,
                        "USER_BLOCKED",
                        "USER",
                        "44444444-4444-4444-4444-444444444444",
                        "Блокировка пользователя",
                        "10.0.0.5",
                        Instant.parse("2026-10-17T10:00:00Z"),
                        Instant.parse("2026-10-17T10:00:01Z"))),
                1, 0, 20);
    }

    @Test
    void admins_returnsPage() throws Exception {
        when(auditLogService.list(eq(AuditActorCategory.ADMIN), isNull(), isNull(),
                isNull(), isNull(), eq(0), eq(20))).thenReturn(page());

        mockMvc.perform(get("/admin/audit/admins").with(user("99999999-9999-9999-9999-999999999999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].action").value("USER_BLOCKED"))
                .andExpect(jsonPath("$.data.entries[0].actorCategory").value("ADMIN"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void moderators_passesActionFilter() throws Exception {
        when(auditLogService.list(eq(AuditActorCategory.MODERATOR), eq("WARNING_ISSUED"), isNull(),
                isNull(), isNull(), eq(0), eq(20))).thenReturn(page());

        mockMvc.perform(get("/admin/audit/moderators")
                        .param("action", "WARNING_ISSUED")
                        .with(user("99999999-9999-9999-9999-999999999999")))
                .andExpect(status().isOk());

        verify(auditLogService).list(AuditActorCategory.MODERATOR, "WARNING_ISSUED", null,
                null, null, 0, 20);
    }

    @Test
    void support_parsesTimeRange() throws Exception {
        when(auditLogService.list(eq(AuditActorCategory.SUPPORT), isNull(), isNull(),
                any(), any(), eq(0), eq(20))).thenReturn(page());

        mockMvc.perform(get("/admin/audit/support")
                        .param("from", "2026-10-01T00:00:00Z")
                        .param("to", "2026-10-17T23:59:59Z")
                        .with(user("99999999-9999-9999-9999-999999999999")))
                .andExpect(status().isOk());

        verify(auditLogService).list(eq(AuditActorCategory.SUPPORT), isNull(), isNull(),
                eq(Instant.parse("2026-10-01T00:00:00Z")),
                eq(Instant.parse("2026-10-17T23:59:59Z")), eq(0), eq(20));
    }

    @Test
    void users_returnsPage() throws Exception {
        when(auditLogService.list(eq(AuditActorCategory.USER), any(), any(),
                any(), any(), any(int.class), any(int.class))).thenReturn(page());

        mockMvc.perform(get("/admin/audit/users").with(user("99999999-9999-9999-9999-999999999999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }
}
