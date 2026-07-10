package com.pki.ra.raservice.registration;

import com.pki.ra.common.model.enums.CsrProfile;

/**
 * Response body returned after a successful self-registration.
 *
 * @param userId    newly created user ID
 * @param username  AD sAMAccountName
 * @param email     contact email
 * @param profileId certificate profile the user registered for
 * @param isActive  always {@code false} on creation — admin must activate
 * @param message   human-readable status message
 */
public record EndEntityRegistrationResponse(
    Long userId,
    String username,
    String email,
    CsrProfile profileId,
    boolean isActive,
    String message
) {}
