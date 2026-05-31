package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Minimal JPA entity representing a PKI-RA system user.
 *
 * <p>Maps to the {@code users} table. Only the fields required for
 * audit-log user resolution are declared here — {@code id}, {@code username},
 * and {@code isActive}. Additional profile fields (email, fullName, etc.)
 * are added when the full user-management branch is merged.
 *
 * <h3>Why not extends BaseAuditEntity?</h3>
 * {@code BaseAuditEntity} will eventually receive a {@code createdByUserId}
 * FK that references this entity. A circular JPA mapping
 * ({@code User → BaseAuditEntity → User}) would be ambiguous.
 * {@code User} therefore manages its own audit columns independently.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_is_active", columnList = "is_active")
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
     * Unique — primary lookup key resolved from Spring Security principal.
     */
    @Column(name = "username", nullable = false, length = 100, unique = true)
    private String username;

    /**
     * {@code false} = soft-deleted — excluded from auth and active queries.
     * Only active users are matched during userId resolution.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
