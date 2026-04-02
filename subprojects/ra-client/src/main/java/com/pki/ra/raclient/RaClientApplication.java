package com.pki.ra.raclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PKI-RA Client Application entry point.
 *
 * <p>Provides REST client integration with the RA service for
 * certificate request submission and lifecycle management.
 *
 * <p>Run with: {@code ./gradlew :ra-client:bootRun}
 *
 * @author pki-ra
 * @since  1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.pki.ra")
public class RaClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaClientApplication.class, args);
    }
}
