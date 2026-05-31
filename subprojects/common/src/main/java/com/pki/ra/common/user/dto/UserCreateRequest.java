package com.pki.ra.common.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/admin/users} — create a new user.
 *
 * @author pki-ra
 * @since  1.0.0
 */
public record UserCreateRequest(

        // AD sAMAccountName — must be unique across all users.
        @NotBlank(message = "username is required")
        @Size(max = 100, message = "username must be ≤ 100 characters")
        String username,

        @Email(message = "email must be a valid email address")
        @Size(max = 200, message = "email must be ≤ 200 characters")
        String email,

        @Size(max = 200, message = "fullName must be ≤ 200 characters")
        String fullName,

        @Size(max = 200, message = "displayName must be ≤ 200 characters")
        String displayName,

        // Optional role IDs to assign at creation — done in same transaction.
        List<Long> roleIds
) {}
