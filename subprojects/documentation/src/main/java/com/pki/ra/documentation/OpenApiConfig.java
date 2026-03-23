package com.pki.ra.documentation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 documentation configuration for all pki-ra REST APIs.
 *
 * <p>Accessible at:
 * <ul>
 *   <li>Swagger UI:  {@code /swagger-ui.html}</li>
 *   <li>API JSON:    {@code /v3/api-docs}</li>
 * </ul>
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Configuration
public class OpenApiConfig {

    /**
     * Produces the root {@link OpenAPI} metadata bean with project info,
     * contact details, license, and Bearer token security scheme.
     *
     * @return configured OpenAPI descriptor
     */
    @Bean
    public OpenAPI pkiRaOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("PKI Registration Authority API")
                .version("1.0.0")
                .description("""
                    REST API for the PKI Registration Authority (RA) system.

                    **Authentication:** Active Directory (LDAP) via Spring Security.
                    Session-based authentication for browser clients.
                    Bearer JWT token for API clients.

                    **Modules:**
                    - UIService: Certificate request and user management APIs
                    - Scheduler: Job management and status APIs
                    """)
                .contact(new Contact()
                    .name("PKI-RA Team")
                    .email("pki-ra-support@example.com"))
                .license(new License()
                    .name("Internal Use Only")
                    .url("https://internal.example.com/licenses/pki-ra")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .name("bearerAuth")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
