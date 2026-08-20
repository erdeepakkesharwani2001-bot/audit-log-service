package com.schwab.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

/**
 * Auditing Configuration for JPA entities
 * 
 * Provides automatic tracking of createdAt/updatedAt timestamps
 * and audit user information on entities with @Audited annotation.
 */
@Configuration
public class AuditingConfig {

    /**
     * Provides the current authenticated user for auditing purposes.
     * If no user is authenticated, returns "system" as the auditor.
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated() 
                && !authentication.getPrincipal().equals("anonymousUser")) {
                return Optional.of(authentication.getName());
            }
            
            return Optional.of("system");
        };
    }

    /**
     * Provides the current date/time for JPA auditing.
     * Uses server time for consistency.
     */
//    @Bean
//    public DateTimeProvider dateTimeProvider() {
//        return () -> Optional.of(TemporalAccessor.from(LocalDateTime.now()));
//    }
}
