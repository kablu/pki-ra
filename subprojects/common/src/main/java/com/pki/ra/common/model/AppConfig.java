package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "app_config",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_app_config_key_type",
        columnNames = { "config_key", "config_type" }
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppConfig extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Field name within the config type, e.g. {@code host}, {@code port}.
     * Unique per {@code config_type} — same key can exist across different types.
     */
    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    /**
     * Groups related keys into one logical config unit, e.g. {@code LDAP}.
     * All rows with the same {@code config_type} are assembled into one DTO
     * by {@code ConfigBean}.
     */
    @Column(name = "config_type", nullable = false, length = 50)
    private String configType;

    /** Plain string value for this field — no JSON, no nesting. */
    @Column(name = "config_value", nullable = false, length = 500)
    private String configValue;

    @Column(name = "description", length = 500)
    private String description;

    /** {@code false} excludes this row from the in-memory cache without deleting it. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
