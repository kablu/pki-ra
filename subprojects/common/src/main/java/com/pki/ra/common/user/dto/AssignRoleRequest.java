package com.pki.ra.common.user.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/admin/users/{id}/roles} — assign a role to a user.
 *
 * @author pki-ra
 * @since  1.0.0
 */
public record AssignRoleRequest(

        @NotNull(message = "roleId is required")
        Long roleId
) {}
