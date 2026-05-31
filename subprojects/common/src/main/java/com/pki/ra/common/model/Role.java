package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Persistent role definition in the PKI-RA system.
 *
 * <p>Each row represents a named permission set. Users are linked to roles
 * via the {@code user_roles} junction table ({@link UserRole}).
 *
 * <h3>Built-in roles (seeded by V1 migration)</h3>
 * <ul>
 *   <li>{@code ROLE_ADMIN}    — full access</li>
 *   <li>{@code ROLE_OPERATOR} — certificate operations</li>
 *   <li>{@code ROLE_AUDITOR}  — read-only audit logs</li>
 *   <li>{@code ROLE_VIEWER}   — read-only configs and status</li>
 * </ul>
 *
 * <h3>Why not extends BaseAuditEntity?</h3>
 * {@code BaseAuditEntity} will receive {@code createdByUserId} FK → {@link User}.
 * {@code User} must not extend {@code BaseAuditEntity} to avoid a circular
 * dependency. {@code Role} follows the same convention for consistency —
 * both carry their own standalone audit columns.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Entity
@Table(
    name = "roles",
    indexes = {
        @Index(name = "idx_roles_is_active", columnList = "is_active")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique role identifier, e.g. {@code ROLE_ADMIN}, {@code ROLE_OPERATOR}.
     * Spring Security convention: prefix with {@code ROLE_}.
     */
    @Column(name = "role_name", nullable = false, length = 50, unique = true)
    private String roleName;

    /** Human-readable description of what this role grants. */
    @Column(name = "description", length = 500)
    private String description;

    /** {@code false} = soft-deleted — excluded from assignment and auth checks. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    // -------------------------------------------------------------------------
    // Standalone audit columns (no BaseAuditEntity — see Javadoc)
    // -------------------------------------------------------------------------

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    @Builder.Default
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 100)
    @Builder.Default
    private String updatedBy = "system";
}
