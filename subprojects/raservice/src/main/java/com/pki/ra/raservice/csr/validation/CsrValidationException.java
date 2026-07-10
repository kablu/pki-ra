package com.pki.ra.raservice.csr.validation;

import org.springframework.http.HttpStatus;
import com.pki.ra.common.exception.PkiRaException;

import java.util.List;

/**
 * Thrown when a submitted CSR fails one or more hard validation checks.
 *
 * <p>Carries the full list of error codes + messages from
 * {@link CsrValidationResult} so the global exception handler can surface
 * all failures in a single 422 response instead of stopping at the first.
 */
public class CsrValidationException extends PkiRaException {

    private final List<String> validationErrors;

    public CsrValidationException(List<String> validationErrors) {
        super(
            "CSR validation failed: " + String.join("; ", validationErrors),
            HttpStatus.UNPROCESSABLE_ENTITY
        );
        this.validationErrors = List.copyOf(validationErrors);
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
