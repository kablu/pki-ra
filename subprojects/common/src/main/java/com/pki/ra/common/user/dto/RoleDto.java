package com.pki.ra.common.user.dto;

import com.pki.ra.common.model.Role;

import java.time.Instant;

/**
 * Read-only API projection of a {@link Role}.
 * Returned by role endpoints and embedded inside {@link UserDto}.
 *
 * @author pki-ra
 * @since  1.0.0
 */
public record RoleDto(
        Long    id,
        String  roleName,
        String  description,
        boolean isActive,
        Instant createdAt,
        String  createdBy
) {
    /** Factory — maps {@link Role} entity → {@link RoleDto}. */
    public static RoleDto from(Role role) {
        return new RoleDto(
                role.getId(),
                role.getRoleName(),
                role.getDescription(),
                role.isActive(),
                role.getCreatedAt(),
                role.getCreatedBy()
        );
    }
}
