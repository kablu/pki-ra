package com.pki.ra.uiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * PKI-RA UIService Application entry point.
 *
 * <p>Provides the REST API layer consumed by the GUI module.
 * Handles certificate request processing, user management,
 * and audit log writing for all RA operations.
 *
 * <p>Run with: {@code ./gradlew :uiservice:bootRun}
 *
 * @author pki-ra
 * @since  1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.pki.ra")
@EntityScan(basePackages = "com.pki.ra.common.model")
@EnableJpaRepositories(basePackages = "com.pki.ra")
@EnableJpaAuditing
public class UiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UiServiceApplication.class, args);
    }
}
