package com.n4d3sh1k4.audit_log_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Топология очереди журнала действий: {@code audit-log-queue} слушает события с
 * routing key {@code audit.log} на общем exchange {@code user-exchange}.
 * Отравленные сообщения уходят в {@code audit-log-queue.dlq}.
 */
@Configuration
public class RabbitConfig {

    public static final String QUEUE = "audit-log-queue";
    public static final String DLQ = QUEUE + ".dlq";
    public static final String EXCHANGE = "user-exchange";
    public static final String DLX = "user-exchange.dlx";
    public static final String ROUTING_KEY = "audit.log";

    @Bean
    public Queue auditLogQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX);
        args.put("x-dead-letter-routing-key", DLQ);
        return new Queue(QUEUE, true, false, false, args);
    }

    @Bean
    public Queue auditLogDlq() {
        return new Queue(DLQ, true);
    }

    @Bean
    public TopicExchange dlx() {
        return new TopicExchange(DLX);
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange("user-exchange");
    }

    @Bean
    public Binding auditDlqBinding(Queue auditLogDlq, TopicExchange dlx) {
        return BindingBuilder.bind(auditLogDlq).to(dlx).with(DLQ);
    }

    @Bean
    public Binding auditBinding(Queue auditLogQueue, TopicExchange exchange) {
        return BindingBuilder.bind(auditLogQueue).to(exchange).with("audit.log");
    }

    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();

        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTrustedPackages("*");

        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
