package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Junction entity mapping a {@link User} to a {@link Role}.
 *
 * <p>Represents one row in the {@code user_roles} table.
 * The composite unique constraint {@code uq_user_roles (user_id, role_id)}
 * prevents the same role from being assigned twice to the same user.
 *
 * <h3>Cascade behaviour</h3>
 * {@code ON DELETE CASCADE} on both FKs — deleting a user or a role
 * automatically removes the corresponding assignment rows.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Entity
@Table(
    name = "user_roles",
    uniqueConstraints = @UniqueConstraint(
        name        = "uq_user_roles",
        columnNames = {"user_id", "role_id"}
    ),
    indexes = {
        @Index(name = "idx_user_roles_user_id", columnList = "user_id"),
        @Index(name = "idx_user_roles_role_id", columnList = "role_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who holds this role. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The role assigned to the user. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /** When this role was assigned. */
    @Column(name = "assigned_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant assignedAt = Instant.now();

    /** Username of the admin who made the assignment. */
    @Column(name = "assigned_by", nullable = false, updatable = false, length = 100)
    @Builder.Default
    private String assignedBy = "system";
}
