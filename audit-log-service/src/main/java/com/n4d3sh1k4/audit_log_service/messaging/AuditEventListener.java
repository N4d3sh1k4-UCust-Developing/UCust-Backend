package com.n4d3sh1k4.audit_log_service.messaging;

import com.n4d3sh1k4.audit_log_service.config.RabbitConfig;
import com.n4d3sh1k4.audit_log_service.service.AuditLogService;
import com.n4d3sh1k4.common.audit.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Потребитель событий журнала действий. Запись идемпотентна по eventId —
 * повторная доставка не создаёт дубль.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventListener {

    private final AuditLogService auditLogService;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onAuditEvent(AuditEvent event) {
        auditLogService.record(event);
        log.debug("Записано событие журнала: action={} target={}/{}",
                event.action(), event.targetType(), event.targetId());
    }
}
