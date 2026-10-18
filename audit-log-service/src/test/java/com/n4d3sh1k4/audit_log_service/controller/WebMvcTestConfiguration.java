package com.n4d3sh1k4.audit_log_service.controller;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Тестовая конфигурация для {@code @WebMvcTest}-слайсов (см. аналогичные файлы
 * в других сервисах): включает реальную SecurityConfig только при полном контексте.
 */
@SpringBootConfiguration
@EnableWebSecurity
@ComponentScan(basePackages = {
        "com.n4d3sh1k4.common.advice"
})
public class WebMvcTestConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.test.webmvc-config", havingValue = "enabled", matchIfMissing = false)
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
