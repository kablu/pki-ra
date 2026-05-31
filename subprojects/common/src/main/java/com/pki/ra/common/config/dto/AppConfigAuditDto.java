package com.pki.ra.common.config.dto;

import com.pki.ra.common.model.AppConfig;

import java.time.Instant;

/**
 * Full projection of one {@code app_config} row including the four
 * {@link com.pki.ra.common.model.BaseAuditEntity} audit columns.
 *
 * <p>Used by admin endpoints to expose the complete audit trail:
 * <ul>
 *   <li>{@code createdBy}  — who inserted this config row</li>
 *   <li>{@code createdAt}  — when it was inserted</li>
 *   <li>{@code updatedBy}  — who last modified it</li>
 *   <li>{@code updatedAt}  — when it was last modified</li>
 * </ul>
 *
 * <p>These four columns are auto-populated by Spring Data JPA Auditing
 * via {@code AuditorAwareImpl} — no manual assignment needed.
 *
 * @param id          surrogate primary key
 * @param configKey   field name within the config type (e.g. {@code host})
 * @param configType  logical group (e.g. {@code LDAP})
 * @param configValue plain string value
 * @param description human-readable purpose of this row
 * @param isActive    {@code false} excludes row from ConfigBean cache
 * @param createdBy   AD username who inserted this row (or {@code system})
 * @param createdAt   UTC timestamp of insertion
 * @param updatedBy   AD username who last modified this row (or {@code system})
 * @param updatedAt   UTC timestamp of last modification
 */
public record AppConfigAuditDto(
        Long    id,
        String  configKey,
        String  configType,
        String  configValue,
        String  description,
        boolean isActive,
        String  createdBy,
        Instant createdAt,
        String  updatedBy,
        Instant updatedAt
) {
    /**
     * Maps an {@link AppConfig} entity (which extends {@code BaseAuditEntity})
     * to this DTO — single point of mapping, no duplication in controllers.
     */
    public static AppConfigAuditDto from(AppConfig entity) {
        return new AppConfigAuditDto(
                entity.getId(),
                entity.getConfigKey(),
                entity.getConfigType(),
                entity.getConfigValue(),
                entity.getDescription(),
                entity.isActive(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt()
        );
    }
}
