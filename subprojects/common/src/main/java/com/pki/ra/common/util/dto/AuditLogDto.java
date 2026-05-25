package com.pki.ra.common.util.dto;

import com.pki.ra.common.model.AuditLog;

import java.time.Instant;

/**
 * Read-only projection of one {@code audit_log} row.
 *
 * <p>Built from {@link AuditLog} via the static {@link #from(AuditLog)} factory
 * — keeps controller and service code free of field-by-field mapping.
 *
 * @param id          surrogate primary key
 * @param username    AD sAMAccountName of the actor
 * @param action      action constant (e.g. {@code CONFIG_REFRESH})
 * @param resourceId  optional: which entity was affected
 * @param description human-readable summary of the action
 * @param ipAddress   originating client IP (null for batch / system jobs)
 * @param outcome     {@code SUCCESS} or {@code FAILURE}
 * @param createdAt   UTC timestamp when this entry was written
 */
public record AuditLogDto(
        Long    id,
        String  username,
        String  action,
        String  resourceId,
        String  description,
        String  ipAddress,
        String  outcome,
        Instant createdAt
) {
    /** Maps an {@link AuditLog} entity to this DTO — null-safe. */
    public static AuditLogDto from(AuditLog entity) {
        return new AuditLogDto(
                entity.getId(),
                entity.getUsername(),
                entity.getAction(),
                entity.getResourceId(),
                entity.getDescription(),
                entity.getIpAddress(),
                entity.getOutcome(),
                entity.getCreatedAt()
        );
    }
}
