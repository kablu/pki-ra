package com.pki.ra.raservice.registration;

import com.pki.ra.common.model.enums.CsrProfile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for end-entity self-registration.
 *
 * <p>The caller must be authenticated (AD JWT). The username in this DTO
 * must match the authenticated identity — the service enforces this check.
 *
 * @param profileId  the certificate profile the end entity intends to use
 * @param username   AD sAMAccountName of the registering user
 * @param email      contact email address
 */
public record EndEntityRegistrationRequest(

    @NotNull(message = "profileId is required")
    CsrProfile profileId,

    @NotBlank(message = "username is required")
    String username,

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid address")
    String email
) {}
