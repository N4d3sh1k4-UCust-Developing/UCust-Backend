package com.n4d3sh1k4.audit_log_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.n4d3sh1k4")
public class AuditLogServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuditLogServiceApplication.class, args);
	}

}
