package com.pki.ra.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource does not exist in the system.
 * Maps to HTTP {@code 404 Not Found}.
 *
 * @author pki-ra
 * @since  1.0.0
 */
public class ResourceNotFoundException extends PkiRaException {

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super("%s not found with identifier: %s".formatted(resourceName, identifier),
              HttpStatus.NOT_FOUND);
    }
}
