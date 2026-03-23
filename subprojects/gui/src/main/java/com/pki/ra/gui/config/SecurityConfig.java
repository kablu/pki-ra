package com.pki.ra.gui.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for Active Directory (AD) authentication.
 *
 * <p>All credentials are stored in {@code application.yml} / environment variables.
 * No passwords or URLs are hardcoded here.
 *
 * <p>Access rules:
 * <ul>
 *   <li>{@code /login}, {@code /css/**}, {@code /js/**} — public</li>
 *   <li>{@code /actuator/health} — public (for load balancer health checks)</li>
 *   <li>Everything else — requires authenticated AD user</li>
 *   <li>{@code /admin/**} — requires {@code ROLE_RA_ADMIN}</li>
 * </ul>
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${pki.ra.ad.domain}")
    private String adDomain;

    @Value("${pki.ra.ad.url}")
    private String adUrl;

    @Value("${pki.ra.ad.root-dn}")
    private String adRootDn;

    /**
     * Main security filter chain — HTTP access rules + form login + logout.
     *
     * @param http HttpSecurity builder
     * @return configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/admin/**").hasRole("RA_ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(1)
                .expiredUrl("/login?expired=true")
            );

        return http.build();
    }

    /**
     * Configures Active Directory LDAP as the authentication provider.
     *
     * @param http AuthenticationManagerBuilder
     * @return {@link AuthenticationManager} backed by AD LDAP
     * @throws Exception if LDAP connection setup fails
     */
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder builder =
            http.getSharedObject(AuthenticationManagerBuilder.class);

        ActiveDirectoryLdapAuthenticationProvider adProvider =
            new ActiveDirectoryLdapAuthenticationProvider(adDomain, adUrl, adRootDn);

        adProvider.setConvertSubErrorCodesToExceptions(true);
        adProvider.setUseAuthenticationRequestCredentials(true);

        builder.authenticationProvider(adProvider);
        return builder.build();
    }
}
