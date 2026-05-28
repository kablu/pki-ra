package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent user profile in the PKI-RA system.
 *
 * <p>Authentication is handled externally by Active Directory / LDAP —
 * this entity stores only profile data and account status.
 * No passwords are stored here.
 *
 * <p>A row is created on first successful AD login via
 * {@link com.pki.ra.common.user.UserManagementService#findOrCreate}.
 * Role assignments are stored in {@link UserRole}.
 *
 * <h3>Why not extends BaseAuditEntity?</h3>
 * {@code BaseAuditEntity} will receive a {@code createdByUserId} FK that
 * references this entity. A circular JPA dependency ({@code User} →
 * {@code BaseAuditEntity} → {@code User}) would cause ambiguous mapping.
 * {@code User} therefore carries its own standalone audit columns.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_is_active",     columnList = "is_active"),
        @Index(name = "idx_users_last_login_at", columnList = "last_login_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * AD sAMAccountName, e.g. {@code john.doe}.
     * Unique — used as the primary lookup key from Spring Security principal.
     */
    @Column(name = "username", nullable = false, length = 100, unique = true)
    private String username;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "display_name", length = 200)
    private String displayName;

    /** {@code false} = soft-deleted — excluded from auth and active queries. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /** Updated by {@code UserManagementService} on every successful login. */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    // -------------------------------------------------------------------------
    // Roles — loaded eagerly for auth checks (typically ≤ 5 roles per user)
    // -------------------------------------------------------------------------

    @OneToMany(
        mappedBy  = "user",
        cascade   = CascadeType.ALL,
        orphanRemoval = true,
        fetch     = FetchType.EAGER
    )
    @Builder.Default
    private List<UserRole> userRoles = new ArrayList<>();

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
