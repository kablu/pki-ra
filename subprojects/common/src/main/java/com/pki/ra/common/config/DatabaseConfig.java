package com.pki.ra.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Central JPA / database configuration for the PKI-RA common module.
 *
 * <p>Always active — no profile restriction — because JPA auditing and
 * transaction management apply to both H2 (dev) and MariaDB (prod).
 *
 * <p>{@code auditorAwareRef = "auditorAwareImpl"} wires to the
 * {@link AuditorAwareImpl} bean registered via {@code @Component("auditorAwareImpl")}.
 * No duplicate {@code @Bean} method here — that would cause
 * {@code BeanDefinitionOverrideException} when component scan and explicit
 * bean declaration both register the same name.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableJpaRepositories(basePackages = "com.pki.ra")
public class DatabaseConfig {
}
