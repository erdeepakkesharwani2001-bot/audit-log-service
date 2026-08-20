package com.schwab.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tamper-Evident Audit Log Service
 * 
 * Entry point for the Spring Boot application that provides:
 * - Append-only audit event logging with SHA-256 hash chain verification
 * - Role-based access control via JWT authentication
 * - Three implementation scenarios: Core, Retention/Redaction, Compliance Reporting
 */
@SpringBootApplication
@EnableScheduling
public class AuditLogServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuditLogServiceApplication.class, args);
	}

}
