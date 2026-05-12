package com.pki.ra.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
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

    /**
     * Shared {@link ObjectMapper} with sensible PKI-RA defaults.
     * {@code @ConditionalOnMissingBean} defers to any ObjectMapper declared
     * by the consuming application (e.g. a custom Spring Boot starter config).
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}

