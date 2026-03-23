package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Persistent audit log entry for all RA operations.
 *
 * <p>Every significant action (certificate request, approval, revocation,
 * login, etc.) produces one {@code AuditLog} record stored in the
 * {@code audit_log} table.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_log_username",   columnList = "username"),
    @Index(name = "idx_audit_log_action",     columnList = "action"),
    @Index(name = "idx_audit_log_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** AD username (sAMAccountName) who triggered the action. */
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    /** Action type, e.g. CERT_REQUEST, CERT_APPROVE, CERT_REVOKE, LOGIN, LOGOUT. */
    @Column(name = "action", nullable = false, length = 100)
    private String action;

    /** Optional resource identifier the action was performed on (e.g. certificate serial). */
    @Nullable
    @Column(name = "resource_id", length = 200)
    private String resourceId;

    /** Human-readable description of what happened. */
    @Nullable
    @Column(name = "description", length = 1000)
    private String description;

    /** HTTP request IP address from which the action was initiated. */
    @Nullable
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /** Outcome of the action: SUCCESS or FAILURE. */
    @Column(name = "outcome", nullable = false, length = 20)
    @Builder.Default
    private String outcome = "SUCCESS";

    /** When this audit entry was recorded. */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
