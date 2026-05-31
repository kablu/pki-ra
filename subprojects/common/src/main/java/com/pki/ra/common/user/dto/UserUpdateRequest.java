package com.pki.ra.common.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/admin/users/{id}} — update an existing user's profile.
 * Role changes use the dedicated assign/remove endpoints.
 *
 * @author pki-ra
 * @since  1.0.0
 */
public record UserUpdateRequest(

        @Email(message = "email must be a valid email address")
        @Size(max = 200, message = "email must be ≤ 200 characters")
        String email,

        @Size(max = 200, message = "fullName must be ≤ 200 characters")
        String fullName,

        @Size(max = 200, message = "displayName must be ≤ 200 characters")
        String displayName,

        // false = soft-delete the user (deactivate via update body).
        Boolean isActive
) {}
