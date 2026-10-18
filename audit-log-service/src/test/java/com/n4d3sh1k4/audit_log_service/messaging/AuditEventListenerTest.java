package com.n4d3sh1k4.audit_log_service.messaging;

import com.n4d3sh1k4.audit_log_service.service.AuditLogService;
import com.n4d3sh1k4.common.audit.AuditActorCategory;
import com.n4d3sh1k4.common.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuditEventListener auditEventListener;

    @Test
    void onAuditEvent_delegatesToService() {
        AuditEvent event = new AuditEvent(UUID.randomUUID(), UUID.randomUUID(),
                AuditActorCategory.ADMIN, "USER_BLOCKED", "USER", UUID.randomUUID().toString(),
                null, "10.0.0.1", Instant.now());

        auditEventListener.onAuditEvent(event);

        verify(auditLogService).record(event);
    }
}
