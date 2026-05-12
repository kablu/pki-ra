package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "app_config",
    uniqueConstraints = @UniqueConstraint(name = "uq_app_config_key", columnNames = "config_key")
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

    /** Logical name used as the cache key, e.g. {@code isSecLdap}. */
    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    /**
     * Discriminator that tells {@code ConfigDtoRegistry} which DTO class to
     * deserialise {@code configValue} into, e.g. {@code LDAP}.
     */
    @Column(name = "config_type", nullable = false, length = 50)
    private String configType;

    /** JSON-serialised DTO payload. Deserialised by {@code ConfigBean} at startup. */
    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "description", length = 500)
    private String description;

    /** {@code false} excludes this row from the in-memory cache without deleting it. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
