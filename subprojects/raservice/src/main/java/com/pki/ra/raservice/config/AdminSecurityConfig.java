package com.pki.ra.raservice.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secures the {@code /api/admin/**} namespace with HTTP Basic authentication.
 *
 * <ul>
 *   <li>Any request to {@code /api/admin/**} requires {@code ROLE_ADMIN}.</li>
 *   <li>Unauthenticated calls return {@code 401} JSON (no browser login-page redirect).</li>
 *   <li>Authenticated-but-unauthorized calls return {@code 403} JSON.</li>
 *   <li>CSRF is disabled — admin endpoints are consumed by REST clients, not browsers.</li>
 * </ul>
 *
 * <p>The in-memory user ({@code admin}/{@code admin123}) is intentionally plain-text
 * for local dev. Replace with LDAP or DB-backed authentication before production.
 */
@Configuration
public class AdminSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/admin/**")
            .authorizeHttpRequests(auth -> auth
                .anyRequest().hasRole("ADMIN")
            )
            .httpBasic(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"status\":401,\"error\":\"Unauthorized\"," +
                        "\"message\":\"Admin credentials required\"}"
                    );
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"status\":403,\"error\":\"Forbidden\"," +
                        "\"message\":\"ROLE_ADMIN is required\"}"
                    );
                })
            );

        return http.build();
    }

    /**
     * In-memory admin user for local development.
     * Credentials: admin / admin123 (plain-text, {noop} prefix).
     */
    @Bean
    public UserDetailsService adminUserDetailsService() {
        UserDetails admin = User.withUsername("admin")
                .password("{noop}admin123")
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }
}
