package com.pki.ra.uiservice.exception;

import com.pki.ra.common.exception.ResourceNotFoundException;

/**
 * Employee specific not-found exception.
 * Extends {@link ResourceNotFoundException} from :common module.
 *
 * @author pki-ra
 * @since  1.0.0
 */
public class EmployeeNotFoundException extends ResourceNotFoundException {

    public EmployeeNotFoundException(Long id) {
        super("Employee", id);
    }

    public EmployeeNotFoundException(String email) {
        super("Employee", email);
    }
}
