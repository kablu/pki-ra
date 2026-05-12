package com.pki.ra.raservice.seed;

import com.pki.ra.common.error.ErrorCatalogRepository;
import com.pki.ra.common.model.ErrorCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds sample error catalog rows for local H2 development.
 * Runs before ApplicationReadyEvent so ErrorCatalogBean cache is populated on startup.
 */
@Component
@Profile("h2")
@Order(2)
public class ErrorCatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ErrorCatalogSeeder.class);

    private final ErrorCatalogRepository repository;

    public ErrorCatalogSeeder(ErrorCatalogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            log.info("ErrorCatalogSeeder: already has data — skipping.");
            return;
        }

        log.info("ErrorCatalogSeeder: seeding error catalog...");

        repository.saveAll(List.of(

            // CERTIFICATE
            row("PKI_CERT_001", "ERR-001", "Certificate not found.",
                "The requested certificate does not exist.", "CERTIFICATE", "ERROR", 404, false),
            row("PKI_CERT_002", "ERR-002", "Certificate has expired.",
                "The certificate validity period has passed.", "CERTIFICATE", "ERROR", 400, false),
            row("PKI_CERT_003", "ERR-003", "Certificate revocation failed.",
                "Unable to revoke the certificate. Check CA connectivity.", "CERTIFICATE", "CRITICAL", 500, true),
            row("PKI_CERT_004", "ERR-004", "Certificate already revoked.",
                "The certificate was previously revoked.", "CERTIFICATE", "WARNING", 409, false),
            row("PKI_CERT_005", "ERR-005", "Certificate generation failed.",
                "CA server returned an error while generating the certificate.", "CERTIFICATE", "CRITICAL", 500, true),

            // AUTH
            row("PKI_AUTH_001", "ERR-101", "Authentication failed. Invalid credentials.",
                "AD authentication rejected the provided credentials.", "AUTH", "ERROR", 401, false),
            row("PKI_AUTH_002", "ERR-102", "Access denied. Insufficient permissions.",
                "User does not have the required role.", "AUTH", "ERROR", 403, false),
            row("PKI_AUTH_003", "ERR-103", "Session expired. Please login again.",
                "The user session token has expired.", "AUTH", "WARNING", 401, false),

            // VALIDATION
            row("PKI_VAL_001", "ERR-201", "Invalid request. Required field missing.",
                "One or more mandatory request fields were not provided.", "VALIDATION", "WARNING", 400, false),
            row("PKI_VAL_002", "ERR-202", "Invalid certificate request format.",
                "The CSR payload does not conform to the expected format.", "VALIDATION", "WARNING", 400, false),

            // NETWORK
            row("PKI_NET_001", "ERR-301", "CA server is unreachable.",
                "Connection to the Certificate Authority timed out.", "NETWORK", "CRITICAL", 503, true),
            row("PKI_NET_002", "ERR-302", "LDAP server is unreachable.",
                "Connection to the AD/LDAP server timed out.", "NETWORK", "CRITICAL", 503, true),

            // SYSTEM
            row("PKI_SYS_001", "ERR-901", "An unexpected system error occurred.",
                "An unhandled exception occurred. Check application logs.", "SYSTEM", "CRITICAL", 500, false),
            row("PKI_SYS_002", "ERR-902", "Service temporarily unavailable.",
                "The service is under maintenance or overloaded.", "SYSTEM", "ERROR", 503, true)

        ));

        log.info("ErrorCatalogSeeder: {} rows inserted.", repository.count());
    }

    private ErrorCatalog row(String internalCode, String externalCode, String message,
                              String description, String category, String severity,
                              int httpStatus, boolean retryable) {
        return ErrorCatalog.builder()
                .internalCode(internalCode)
                .externalCode(externalCode)
                .message(message)
                .description(description)
                .category(category)
                .severity(severity)
                .httpStatus(httpStatus)
                .isRetryable(retryable)
                .isActive(true)
                .build();
    }
}
