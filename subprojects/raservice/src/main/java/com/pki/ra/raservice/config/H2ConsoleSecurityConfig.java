package com.pki.ra.raservice.config;

import org.h2.server.web.JakartaWebServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Registers the H2 web console servlet and opens its URL under the {@code h2} profile.
 *
 * <p>Spring Boot 4 removed {@code H2ConsoleAutoConfiguration}, so the servlet
 * must be registered manually via {@link ServletRegistrationBean}.
 *
 * <p>Three Spring Security adjustments are also required:
 * <ol>
 *   <li>Permit {@code /h2-console/**} without authentication.</li>
 *   <li>Disable CSRF — the console POSTs without a CSRF token.</li>
 *   <li>{@code frameOptions → sameOrigin} — console UI renders inside an {@code <iframe>}.</li>
 * </ol>
 */
@Configuration
@Profile("h2")
public class H2ConsoleSecurityConfig {

    /** Manually registers JakartaWebServlet — removed from Spring Boot 4 auto-configuration. */
    @Bean
    public ServletRegistrationBean<JakartaWebServlet> h2ConsoleServlet() {
        ServletRegistrationBean<JakartaWebServlet> registration =
                new ServletRegistrationBean<>(new JakartaWebServlet(), "/h2-console/*");
        registration.addInitParameter("webAllowOthers", "false");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/h2-console/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/h2-console/**").permitAll()
            )
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            );

        return http.build();
    }
}
