package com.pki.ra.raservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Opens the H2 web console under the {@code h2} profile.
 *
 * Three things are required for the console to work inside a browser:
 * <ol>
 *   <li>Permit {@code /h2-console/**} without authentication.</li>
 *   <li>Disable CSRF for that path — the console POSTs without a CSRF token.</li>
 *   <li>Allow same-origin frames — the console UI renders inside an {@code <iframe>}.</li>
 * </ol>
 *
 * All other paths remain protected by the default security filter chain.
 */
@Configuration
@Profile("h2")
public class H2ConsoleSecurityConfig {

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
