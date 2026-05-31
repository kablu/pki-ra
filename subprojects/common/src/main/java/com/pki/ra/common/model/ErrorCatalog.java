package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Stores all application error codes and messages.
 *
 * Each row represents one error. Internal code is used inside the application;
 * external code is what gets exposed to API consumers / end users.
 */
@Entity
@Table(
    name = "error_catalog",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_error_internal_code", columnNames = "internal_code"),
        @UniqueConstraint(name = "uq_error_external_code", columnNames = "external_code")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorCatalog extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Internal error code used inside the application. e.g. PKI_CERT_001 */
    @Column(name = "internal_code", nullable = false, length = 50)
    private String internalCode;

    /** External error code exposed to API consumers / UI. e.g. ERR-001 */
    @Column(name = "external_code", nullable = false, length = 50)
    private String externalCode;

    /** Short human-readable message shown to the end user. */
    @Column(name = "message", nullable = false, length = 500)
    private String message;

    /** Detailed description for developers / support team — not shown to users. */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Logical grouping of the error.
     * e.g. CERTIFICATE, AUTH, VALIDATION, NETWORK, SYSTEM
     */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /**
     * Severity level of the error.
     * e.g. INFO, WARNING, ERROR, CRITICAL
     */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    /**
     * HTTP status code to return when this error is raised.
     * e.g. 400, 401, 403, 404, 500
     */
    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    /** Whether the failed operation can be safely retried by the client. */
    @Column(name = "is_retryable", nullable = false)
    @Builder.Default
    private boolean isRetryable = false;

    /** false = error is deactivated and excluded from cache without deleting the row. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
