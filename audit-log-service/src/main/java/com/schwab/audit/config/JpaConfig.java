package com.schwab.audit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA and Hibernate Configuration
 * 
 * Configures:
 * - Entity scanning and repository discovery
 * - Transaction management
 * - JPA auditing for createdAt/updatedAt timestamps
 * - Lazy loading prevention via open-in-view=false
 */

@Configuration
@EnableJpaRepositories(
        basePackages = "com.schwab.audit.repository",
        repositoryImplementationPostfix = "Impl"
)
@EnableTransactionManagement
@EnableJpaAuditing(
        auditorAwareRef = "auditorProvider"
)
public class JpaConfig {

    /**
     * JPA is configured via application.properties:
     * - spring.jpa.hibernate.ddl-auto=validate
     * - spring.jpa.show-sql=false
     * - spring.jpa.properties.hibernate.dialect=PostgreSQLDialect
     * - spring.jpa.properties.hibernate.jdbc.batch_size=10
     * - spring.jpa.open-in-view=false (prevents lazy loading issues)
     * 
     * Flyway is configured for database migrations:
     * - spring.flyway.locations=classpath:db/migration
     * - spring.flyway.baseline-on-migrate=true
     */
}
