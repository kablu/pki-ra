package com.pki.ra.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Central JPA / database configuration for the PKI-RA common module.
 *
 * <p>This configuration is always active (no profile restriction) because
 * JPA auditing and transaction management apply to both H2 (dev) and
 * MariaDB (production) environments.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li><strong>@EnableJpaAuditing</strong> — activates {@code @CreatedDate}, {@code @CreatedBy},
 *       {@code @LastModifiedDate}, {@code @LastModifiedBy} on {@link com.pki.ra.common.model.BaseAuditEntity}.</li>
 *   <li><strong>@EnableTransactionManagement</strong> — enables {@code @Transactional} on
 *       service methods across all modules.</li>
 *   <li><strong>@EnableJpaRepositories</strong> — scans {@code com.pki.ra} for all
 *       Spring Data JPA repository interfaces.</li>
 *   <li><strong>auditorAwareBean</strong> — delegates to {@link AuditorAwareImpl} to resolve
 *       the current AD username from the Spring Security context.</li>
 * </ul>
 */
@Configuration
@EnableTransactionManagement
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableJpaRepositories(basePackages = "com.pki.ra")
public class DatabaseConfig {

    /**
     * Exposes {@link AuditorAwareImpl} as a named bean consumed by
     * {@code @EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")}.
     *
     * <p>The bean is also declared as a {@code @Component} on the impl class itself;
     * this explicit declaration here makes the dependency visible to readers of this config.
     */
    @Bean
    public AuditorAware<String> auditorAwareImpl() {
        return new AuditorAwareImpl();
    }
}
