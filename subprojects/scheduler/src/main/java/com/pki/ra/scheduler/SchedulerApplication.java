package com.pki.ra.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PKI-RA Scheduler Application entry point.
 *
 * <p>Runs Quartz-based background jobs for certificate lifecycle management:
 * <ul>
 *   <li>Certificate expiry notification (daily)</li>
 *   <li>CRL update job (every 6 hours)</li>
 *   <li>Audit log archiving job (weekly)</li>
 * </ul>
 *
 * <p>Quartz job store uses the same PostgreSQL database ({@code pki_ra})
 * via the {@code QRTZ_*} tables created by Flyway migration.
 *
 * <p>Run with: {@code ./gradlew :scheduler:bootRun}
 *
 * @author pki-ra
 * @since  1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.pki.ra")
@EntityScan(basePackages = "com.pki.ra.common.model")
@EnableJpaRepositories(basePackages = "com.pki.ra")
@EnableJpaAuditing
@EnableScheduling
public class SchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerApplication.class, args);
    }
}
