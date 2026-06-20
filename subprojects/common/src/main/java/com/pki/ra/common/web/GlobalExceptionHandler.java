package com.pki.ra.common.web;

import com.pki.ra.common.error.ErrorCatalogProvider;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Default exception handler — active in every PKI-RA module that does not
 * declare its own concrete handler.
 *
 * <h3>Design</h3>
 * All 8 common handlers live in {@link AbstractGlobalExceptionHandler}.
 * This class simply activates them via {@code @RestControllerAdvice} —
 * no logic lives here.
 *
 * <h3>Module-specific exceptions</h3>
 * If a module (e.g. {@code caservice}) needs to handle its own exception types,
 * it defines its own concrete handler that extends
 * {@link AbstractGlobalExceptionHandler} and adds only the extra
 * {@code @ExceptionHandler} methods:
 * <pre>{@code
 * // caservice — GlobalExceptionHandler.java
 * @RestControllerAdvice
 * public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {
 *     public GlobalExceptionHandler(ErrorCatalogProvider catalog) { super(catalog); }
 *
 *     @ExceptionHandler(CaConnectivityException.class)
 *     public ResponseEntity<ErrorResponse> handleCa(CaConnectivityException ex) { ... }
 * }
 * }</pre>
 * When a module provides its own {@code @RestControllerAdvice} that extends
 * {@link AbstractGlobalExceptionHandler}, Spring will pick up BOTH — but since
 * the module's class is more specific, its handlers take priority for module
 * exception types. The common ones are still covered by the base class methods
 * inherited into the module's handler.
 *
 * @see AbstractGlobalExceptionHandler
 * @author pki-ra
 * @since  1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {

    public GlobalExceptionHandler(ErrorCatalogProvider catalog) {
        super(catalog);
    }

    // All 8 handlers are inherited from AbstractGlobalExceptionHandler.
    // Add module-specific @ExceptionHandler methods here only if this
    // class is customised for a specific module.
}
