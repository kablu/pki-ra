package com.pki.ra.common.user.dto;

import com.pki.ra.common.model.User;

import java.time.Instant;
import java.util.List;

/**
 * Read-only API projection of a {@link User}, including their assigned roles.
 * Returned by all user endpoints.
 *
 * @author pki-ra
 * @since  1.0.0
 */
public record UserDto(
        Long        id,
        String      username,
        String      email,
        String      fullName,
        String      displayName,
        boolean     isActive,
        Instant     lastLoginAt,
        List<RoleDto> roles,
        Instant     createdAt,
        String      createdBy,
        Instant     updatedAt,
        String      updatedBy
) {
    /** Factory — maps {@link User} entity → {@link UserDto} (with roles). */
    public static UserDto from(User user) {
        List<RoleDto> roles = user.getUserRoles()
                .stream()
                .map(ur -> RoleDto.from(ur.getRole()))
                .toList();

        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getDisplayName(),
                user.isActive(),
                user.getLastLoginAt(),
                roles,
                user.getCreatedAt(),
                user.getCreatedBy(),
                user.getUpdatedAt(),
                user.getUpdatedBy()
        );
    }
}
