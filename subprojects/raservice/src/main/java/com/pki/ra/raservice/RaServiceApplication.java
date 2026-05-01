package com.pki.ra.raservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * PKI-RA RA Service — Registration Authority backend.
 *
 * <p>scanBasePackages = "com.pki.ra" ensures all @Configuration / @Component
 * beans from :common (DatabaseConfig, H2ConnectivityChecker, AuditorAwareImpl,
 * DataSourceConfig) are picked up alongside raservice-local beans.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.pki.ra")
@EntityScan(basePackages = "com.pki.ra.common.model")
public class RaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaServiceApplication.class, args);
    }
}
