package com.pki.ra.raservice.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secures the {@code /api/ra/**} namespace for RA operations.
 *
 * <ul>
 *   <li>Submit CSR: any authenticated user (ROLE_ADMIN or ROLE_OPERATOR)</li>
 *   <li>Approve/Reject: requires ROLE_ADMIN or ROLE_OPERATOR</li>
 *   <li>Query certificates: any authenticated user</li>
 * </ul>
 */
@Configuration
public class RaSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain raFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/ra/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/ra/requests/*/approve", "/api/ra/requests/*/reject")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"status\":401,\"error\":\"Unauthorized\"," +
                        "\"message\":\"Authentication required\"}"
                    );
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"status\":403,\"error\":\"Forbidden\"," +
                        "\"message\":\"Insufficient permissions\"}"
                    );
                })
            );

        return http.build();
    }
}
