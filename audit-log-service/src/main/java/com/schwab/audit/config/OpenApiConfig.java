package com.schwab.audit.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the public OpenAPI document rendered by Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH_SCHEME = "Bearer Authentication";

    @Bean
    public OpenAPI auditLogOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Audit Log Service API")
                        .version("1.0.0")
                        .description("REST API for creating, searching, verifying, exporting, and administering tamper-evident audit events. "
                                + "Authenticate with the login endpoint, then use **Authorize** and paste the returned JWT token.")
                        .contact(new Contact().name("Audit Log Service Team"))
                        .license(new License().name("Internal use")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT returned by POST /api/v1/auth/login")));
    }
}
