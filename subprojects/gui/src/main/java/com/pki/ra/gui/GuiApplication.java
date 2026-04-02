package com.pki.ra.gui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * PKI-RA GUI Application entry point.
 *
 * <p>Bootstraps the Thymeleaf web UI with:
 * <ul>
 *   <li>Active Directory (LDAP) authentication</li>
 *   <li>Role-based access control (RBAC)</li>
 *   <li>JPA auditing with AD principal as the auditor</li>
 *   <li>Async audit log writes</li>
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :gui:bootRun}
 *
 * @author pki-ra
 * @since  1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.pki.ra")
@EntityScan(basePackages = "com.pki.ra.common.model")
@EnableJpaRepositories(basePackages = "com.pki.ra")
@EnableJpaAuditing
@EnableAsync
public class GuiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuiApplication.class, args);
    }
}
