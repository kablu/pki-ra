package com.pki.ra.uiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * PKI-RA UIService Application entry point.
 *
 * <p>Provides the REST API layer consumed by the GUI module.
 * Handles certificate request processing, user management,
 * and audit log writing for all RA operations.
 *
 * <p>scanBasePackages = "com.pki.ra" ensures :common config beans
 * (DatabaseConfig, H2ConnectivityChecker, AuditorAwareImpl, DataSourceConfig)
 * are picked up — @EnableJpaAuditing and @EnableJpaRepositories are already
 * declared in DatabaseConfig and must NOT be repeated here (duplicate bean error).
 *
 * <p>Run with: {@code ./gradlew :uiservice:bootRun}
 *
 * @author pki-ra
 * @since  1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.pki.ra")
@EntityScan(basePackages = {
    "com.pki.ra.common.model",    // AuditLog, BaseAuditEntity
    "com.pki.ra.uiservice.model"  // Employee — was missing, causing silent entity scan gap
})
public class UiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UiServiceApplication.class, args);
    }
}
