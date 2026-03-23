package com.pki.ra.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base runtime exception for all pki-ra application errors.
 *
 * <p>Subclasses must provide an {@link HttpStatus} that the
 * {@code GlobalExceptionHandler} uses to set the HTTP response status.
 *
 * @author pki-ra
 * @since  1.0.0
 */
public class PkiRaException extends RuntimeException {

    private final HttpStatus status;

    public PkiRaException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public PkiRaException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
