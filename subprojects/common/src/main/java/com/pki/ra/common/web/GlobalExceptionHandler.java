package com.pki.ra.common.web;

import com.pki.ra.common.exception.AppException;
import com.pki.ra.common.exception.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for all PKI-RA REST controllers.
 *
 * <h3>Security contract</h3>
 * <ul>
 *   <li>API response contains ONLY: {@code correlationId}, external error code,
 *       external message, HTTP status, retryable flag, timestamp.</li>
 *   <li>Internal codes, internal messages, stack traces, class names, SQL,
 *       LDAP DNs, host names — NEVER in API response.</li>
 *   <li>Internal details are always logged at ERROR level with the correlation ID
 *       so operations teams can cross-reference without exposing details to callers.</li>
 * </ul>
 *
 * <h3>Handler priority (most specific → least specific)</h3>
 * <ol>
 *   <li>{@link AppException}                — domain errors from error catalog</li>
 *   <li>{@link MethodArgumentNotValidException} — @Valid bean validation failures</li>
 *   <li>{@link ConstraintViolationException}    — @Validated constraint violations</li>
 *   <li>{@link HttpMessageNotReadableException} — malformed JSON body</li>
 *   <li>{@link AuthenticationException}         — Spring Security auth failure</li>
 *   <li>{@link AccessDeniedException}           — Spring Security access denied</li>
 *   <li>{@link MethodArgumentTypeMismatchException} — path/query param type error</li>
 *   <li>{@link Exception}                       — catch-all (generic 500)</li>
 * </ol>
 *
 * @see AppException
 * @see ErrorResponse
 * @author pki-ra
 * @since  1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Generic fallback codes — used when exception is NOT from error catalog
    private static final String GENERIC_EXTERNAL_CODE       = "ERR-999";
    private static final String VALIDATION_EXTERNAL_CODE    = "ERR-201";
    private static final String AUTH_EXTERNAL_CODE          = "ERR-101";
    private static final String ACCESS_DENIED_EXTERNAL_CODE = "ERR-102";
    private static final String BAD_REQUEST_EXTERNAL_CODE   = "ERR-201";

    // =========================================================================
    // 1 — AppException (domain errors from error catalog)
    // =========================================================================

    /**
     * Handles all {@link AppException} instances thrown by the service layer.
     *
     * <p>Logs: correlationId + internalCode + INTERNAL message (never exposed).
     * Returns: correlationId + EXTERNAL code + EXTERNAL message only.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {

        // Log internal details — safe, never reaches API response
        log.error("[{}] correlationId='{}' httpStatus={} — {}",
                ex.getInternalCode(), ex.getCorrelationId(),
                ex.getHttpStatus(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                ex.getCorrelationId(),
                ex.getExternalCode(),       // ERR-001, ERR-101, etc. — safe
                ex.getExternalMessage(),    // sanitized, user-friendly — safe
                ex.getHttpStatus(),
                ex.isRetryable()
        );

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    // =========================================================================
    // 2 — Bean validation (@Valid, @Validated)
    // =========================================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String correlationId = newCorrelationId();

        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("Validation failed correlationId='{}' — {}", correlationId, details);

        ErrorResponse response = ErrorResponse.of(
                correlationId,
                VALIDATION_EXTERNAL_CODE,
                "Invalid request: " + details,   // field messages are user-safe
                HttpStatus.BAD_REQUEST.value(),
                false
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String correlationId = newCorrelationId();

        String details = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));

        log.warn("Constraint violation correlationId='{}' — {}", correlationId, details);

        ErrorResponse response = ErrorResponse.of(
                correlationId,
                VALIDATION_EXTERNAL_CODE,
                "Invalid request: " + details,
                HttpStatus.BAD_REQUEST.value(),
                false
        );
        return ResponseEntity.badRequest().body(response);
    }

    // =========================================================================
    // 3 — Malformed JSON body
    // =========================================================================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        String correlationId = newCorrelationId();

        // Log the technical detail internally — never expose parse errors to clients
        log.warn("Malformed request body correlationId='{}' — {}", correlationId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                correlationId,
                BAD_REQUEST_EXTERNAL_CODE,
                "Request body is missing or malformed.",
                HttpStatus.BAD_REQUEST.value(),
                false
        );
        return ResponseEntity.badRequest().body(response);
    }

    // =========================================================================
    // 4 — Spring Security exceptions
    // =========================================================================

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        String correlationId = newCorrelationId();

        // Log at WARN — not ERROR (expected for bad credentials)
        log.warn("Authentication failure correlationId='{}' — {}", correlationId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                correlationId,
                AUTH_EXTERNAL_CODE,
                "Authentication failed. Invalid credentials.",   // safe, generic
                HttpStatus.UNAUTHORIZED.value(),
                false
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        String correlationId = newCorrelationId();

        log.warn("Access denied correlationId='{}' — {}", correlationId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                correlationId,
                ACCESS_DENIED_EXTERNAL_CODE,
                "Access denied. Insufficient permissions.",      // safe, generic
                HttpStatus.FORBIDDEN.value(),
                false
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // =========================================================================
    // 5 — Path/query param type mismatch
    // =========================================================================

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String correlationId = newCorrelationId();

        // Safe to include param name — it comes from our own API definition
        String safe = "Invalid value for parameter '" + ex.getName() + "'.";
        log.warn("Type mismatch correlationId='{}' param='{}' value='{}' — {}",
                correlationId, ex.getName(), ex.getValue(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                correlationId,
                BAD_REQUEST_EXTERNAL_CODE,
                safe,
                HttpStatus.BAD_REQUEST.value(),
                false
        );
        return ResponseEntity.badRequest().body(response);
    }

    // =========================================================================
    // 6 — Catch-all (500)
    // =========================================================================

    /**
     * Catch-all for any unexpected exception.
     *
     * <p><strong>Security:</strong> The exception message is intentionally NOT
     * included in the API response — it may contain SQL, LDAP DNs, file paths,
     * class names, or other sensitive technical details.
     * Only a generic message + correlationId is returned.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        String correlationId = newCorrelationId();

        // Full stack trace in log — NEVER in response
        log.error("Unhandled exception correlationId='{}' — {}",
                correlationId, ex.getMessage(), ex);

        ErrorResponse response = ErrorResponse.of(
                correlationId,
                GENERIC_EXTERNAL_CODE,
                "An unexpected error occurred. Use correlationId to contact support.",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                false
        );
        return ResponseEntity.internalServerError().body(response);
    }

    // =========================================================================
    // Private helper
    // =========================================================================

    private String newCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
