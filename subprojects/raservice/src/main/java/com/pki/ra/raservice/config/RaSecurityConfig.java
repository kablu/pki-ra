package com.pki.ra.raservice.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secures the {@code /api/ra/**} namespace with role-based access control.
 *
 * <h3>Endpoint Protection Matrix</h3>
 * <pre>
 *   ADMIN only:
 *     GET  /admin/config/workflow          — read workflow config
 *     PUT  /admin/config/workflow          — update workflow config
 *     GET  /requests/summary              — dashboard counts
 *     GET  /operators                     — operator list for assignment
 *     POST /requests/{id}/assign          — assign to operator
 *     POST /requests/{id}/close           — close permanently
 *     POST /requests/{id}/retry           — retry failed CA call
 *     GET  /requests/{id}/history         — transition history
 *
 *   ADMIN or OPERATOR:
 *     GET  /requests                      — list/filter requests
 *     GET  /requests/{id}                 — request details
 *     GET  /requests/by-request-id/{reqId}— lookup by request ID
 *     GET  /requests/by-txn-id/{txnId}   — lookup by client txn ID
 *     GET  /requests/pool                 — available for pickup
 *     GET  /requests/my-work              — my picked-up requests
 *     GET  /requests/pending-check        — reviewed, ready for Checker
 *     POST /requests/{id}/pickup          — pick up from pool
 *     POST /requests/{id}/return          — return to Admin
 *     POST /requests/{id}/approve         — approve (SINGLE mode)
 *     POST /requests/{id}/reject          — reject
 *     POST /requests/{id}/review          — Maker submits remarks (DUAL)
 *     POST /requests/{id}/accept          — Checker accepts (DUAL)
 *
 *   Any authenticated:
 *     POST /requests                      — submit CSR
 *     GET  /certificates/**               — view/download certificates
 * </pre>
 */
@Configuration
public class RaSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain raFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/ra/**")
            .authorizeHttpRequests(auth -> auth

                // --- ADMIN ONLY ---
                .requestMatchers("/api/ra/admin/**")
                    .hasRole("ADMIN")
                .requestMatchers("/api/ra/requests/summary")
                    .hasRole("ADMIN")
                .requestMatchers("/api/ra/operators")
                    .hasRole("ADMIN")
                .requestMatchers("/api/ra/requests/*/assign")
                    .hasRole("ADMIN")
                .requestMatchers("/api/ra/requests/*/close")
                    .hasRole("ADMIN")
                .requestMatchers("/api/ra/requests/*/retry")
                    .hasRole("ADMIN")
                .requestMatchers("/api/ra/requests/*/history")
                    .hasAnyRole("ADMIN", "AUDITOR")

                // --- ADMIN or OPERATOR ---
                .requestMatchers("/api/ra/requests/pool")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/ra/requests/my-work")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/ra/requests/pending-check")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/ra/requests/*/pickup")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/ra/requests/*/pickup-check")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/ra/requests/*/return")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/ra/requests/*/approve")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/ra/requests/*/reject")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/ra/requests/*/review")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/ra/requests/*/accept")
                    .hasAnyRole("ADMIN", "OPERATOR")

                // --- ADMIN or OPERATOR or AUDITOR: read requests ---
                .requestMatchers(HttpMethod.GET, "/api/ra/requests/**")
                    .hasAnyRole("ADMIN", "OPERATOR", "AUDITOR")

                // --- Any authenticated: submit CSR ---
                .requestMatchers(HttpMethod.POST, "/api/ra/requests")
                    .authenticated()

                // --- Certificates: any authenticated ---
                .requestMatchers("/api/ra/certificates/**")
                    .authenticated()

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
                        "\"message\":\"Insufficient permissions for this operation\"}"
                    );
                })
            );

        return http.build();
    }
}
