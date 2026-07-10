package com.pki.ra.common.web;

import com.pki.ra.common.error.ErrorCatalogProvider;
import com.pki.ra.common.error.dto.ErrorCatalogDto;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Abstract base class for PKI-RA REST exception handlers.
 *
 * <h3>What this provides</h3>
 * All 8 common handlers in one place — defined once, inherited by every module:
 * <ol>
 *   <li>{@link AppException}                         — domain errors from error catalog</li>
 *   <li>{@link MethodArgumentNotValidException}      — @Valid bean validation failures</li>
 *   <li>{@link ConstraintViolationException}         — @Validated constraint violations</li>
 *   <li>{@link HttpMessageNotReadableException}      — malformed JSON body</li>
 *   <li>{@link AuthenticationException}              — Spring Security auth failure</li>
 *   <li>{@link AccessDeniedException}                — Spring Security access denied</li>
 *   <li>{@link MethodArgumentTypeMismatchException}  — path/query param type mismatch</li>
 *   <li>{@link Exception}                            — catch-all (generic 500)</li>
 * </ol>
 *
 * <h3>Catalog-driven fallback codes — zero hardcoding</h3>
 * Non-{@link AppException} handlers look up their error metadata from the
 * {@link ErrorCatalogProvider} cache using well-known internal codes:
 * <pre>
 *   PKI_SYS_001 → generic 500 fallback
 *   PKI_AUTH_001 → authentication failed (401)
 *   PKI_AUTH_002 → access denied (403)
 *   PKI_VAL_001  → validation / bad request (400)
 * </pre>
 * If a code is absent from the catalog (e.g. during tests or misconfiguration),
 * static string literals serve as a hard safety net — the handler never fails.
 *
 * <h3>Per-module extensibility</h3>
 * Each module declares its own concrete {@code GlobalExceptionHandler} that
 * extends this class and adds only the module-specific handlers it needs:
 * <pre>{@code
 * // raservice/GlobalExceptionHandler.java
 * @RestControllerAdvice
 * public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {
 *     public GlobalExceptionHandler(ErrorCatalogProvider catalog) { super(catalog); }
 *     // All 8 common handlers are inherited — nothing extra needed for most modules.
 * }
 *
 * // caservice/GlobalExceptionHandler.java
 * @RestControllerAdvice
 * public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {
 *     public GlobalExceptionHandler(ErrorCatalogProvider catalog) { super(catalog); }
 *
 *     @ExceptionHandler(CaException.class)
 *     public ResponseEntity<ErrorResponse> handleCaException(CaException ex) { ... }
 * }
 * }</pre>
 *
 * <h3>Security contract — never changes</h3>
 * <ul>
 *   <li>API response contains ONLY: correlationId, external code, external message,
 *       HTTP status, retryable flag, timestamp.</li>
 *   <li>Internal codes, stack traces, SQL, LDAP DNs, class names — NEVER in response.</li>
 *   <li>Full details are always logged with the correlation ID for cross-referencing.</li>
 * </ul>
 *
 * @see ErrorCatalogProvider
 * @see AppException
 * @see ErrorResponse
 * @author pki-ra
 * @since  1.0.0
 */
public abstract class AbstractGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractGlobalExceptionHandler.class);

    // ── Well-known internal codes used to look up fallback responses from the catalog ──
    // These map to rows in error_catalog that every module is expected to seed.
    private static final String CODE_GENERIC    = "PKI_SYS_001";   // generic 500
    private static final String CODE_AUTH       = "PKI_AUTH_001";  // authentication failed
    private static final String CODE_FORBIDDEN  = "PKI_AUTH_002";  // access denied
    private static final String CODE_VALIDATION = "PKI_VAL_001";   // bad request / validation

    // ── Hard safety-net literals — used only when catalog lookup returns empty ──
    // These are never returned to callers if the catalog is loaded correctly.
    private static final String FALLBACK_GENERIC_EXT_CODE    = "ERR-999";
    private static final String FALLBACK_AUTH_EXT_CODE       = "ERR-101";
    private static final String FALLBACK_FORBIDDEN_EXT_CODE  = "ERR-102";
    private static final String FALLBACK_VALIDATION_EXT_CODE = "ERR-201";

    private final ErrorCatalogProvider catalog;

    /**
     * @param catalog the error catalog provider — used to look up external codes and
     *                messages for non-{@link AppException} handlers
     */
    protected AbstractGlobalExceptionHandler(ErrorCatalogProvider catalog) {
        this.catalog = catalog;
    }

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

        log.error("[{}] correlationId='{}' httpStatus={} — {}",
                ex.getInternalCode(), ex.getCorrelationId(),
                ex.getHttpStatus(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                ex.getCorrelationId(),
                ex.getExternalCode(),
                ex.getExternalMessage(),
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

        ErrorCatalogDto entry = catalogEntry(CODE_VALIDATION);
        ErrorResponse response = ErrorResponse.of(
                correlationId,
                entry.externalCode(),
                "Invalid request: " + details,   // field messages are user-safe
                HttpStatus.BAD_REQUEST.value(),
                entry.isRetryable()
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

        ErrorCatalogDto entry = catalogEntry(CODE_VALIDATION);
        ErrorResponse response = ErrorResponse.of(
                correlationId,
                entry.externalCode(),
                "Invalid request: " + details,
                HttpStatus.BAD_REQUEST.value(),
                entry.isRetryable()
        );
        return ResponseEntity.badRequest().body(response);
    }

    // =========================================================================
    // 3 — Malformed JSON body
    // =========================================================================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        String correlationId = newCorrelationId();

        log.warn("Malformed request body correlationId='{}' — {}", correlationId, ex.getMessage());

        ErrorCatalogDto entry = catalogEntry(CODE_VALIDATION);
        ErrorResponse response = ErrorResponse.of(
                correlationId,
                entry.externalCode(),
                "Request body is missing or malformed.",
                HttpStatus.BAD_REQUEST.value(),
                entry.isRetryable()
        );
        return ResponseEntity.badRequest().body(response);
    }

    // =========================================================================
    // 4 — Spring Security exceptions
    // =========================================================================

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        String correlationId = newCorrelationId();

        log.warn("Authentication failure correlationId='{}' — {}", correlationId, ex.getMessage());

        ErrorCatalogDto entry = catalogEntry(CODE_AUTH);
        ErrorResponse response = ErrorResponse.of(
                correlationId,
                entry.externalCode(),
                entry.message(),   // catalog message — already user-safe
                HttpStatus.UNAUTHORIZED.value(),
                entry.isRetryable()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        String correlationId = newCorrelationId();

        log.warn("Access denied correlationId='{}' — {}", correlationId, ex.getMessage());

        ErrorCatalogDto entry = catalogEntry(CODE_FORBIDDEN);
        ErrorResponse response = ErrorResponse.of(
                correlationId,
                entry.externalCode(),
                entry.message(),   // catalog message — already user-safe
                HttpStatus.FORBIDDEN.value(),
                entry.isRetryable()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // =========================================================================
    // 5 — Path/query param type mismatch
    // =========================================================================

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String correlationId = newCorrelationId();

        // Safe — parameter name comes from our own API definition, not user input
        String safe = "Invalid value for parameter '" + ex.getName() + "'.";
        log.warn("Type mismatch correlationId='{}' param='{}' value='{}' — {}",
                correlationId, ex.getName(), ex.getValue(), ex.getMessage());

        ErrorCatalogDto entry = catalogEntry(CODE_VALIDATION);
        ErrorResponse response = ErrorResponse.of(
                correlationId,
                entry.externalCode(),
                safe,
                HttpStatus.BAD_REQUEST.value(),
                entry.isRetryable()
        );
        return ResponseEntity.badRequest().body(response);
    }

    // =========================================================================
    // 6 — Catch-all (500)
    // =========================================================================

    /**
     * Catch-all for any unexpected exception.
     *
     * <p><strong>Security:</strong> {@code ex.getMessage()} is intentionally NOT
     * included in the API response — it may contain SQL, LDAP DNs, file paths,
     * class names, or other sensitive details.
     * Only a generic message + correlationId is returned.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        String correlationId = newCorrelationId();

        log.error("Unhandled exception correlationId='{}' — {}",
                correlationId, ex.getMessage(), ex);

        ErrorCatalogDto entry = catalogEntry(CODE_GENERIC);
        ErrorResponse response = ErrorResponse.of(
                correlationId,
                entry.externalCode(),
                entry.message(),   // catalog message — generic, user-safe
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                entry.isRetryable()
        );
        return ResponseEntity.internalServerError().body(response);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Looks up an error catalog entry by internal code.
     * Falls back to a safe static value if the code is not in the cache
     * (e.g. during startup before the catalog is loaded, or in test contexts).
     *
     * <p>The fallback uses the error code that corresponds to {@code internalCode}
     * by convention — so the handler never breaks, even in misconfigured environments.
     *
     * @param internalCode one of the well-known PKI_SYS_001 / PKI_AUTH_001 / … codes
     * @return the catalog entry, or a static-fallback DTO — never null
     */
    private ErrorCatalogDto catalogEntry(String internalCode) {
        return catalog.getByInternalCode(internalCode)
                .orElseGet(() -> {
                    log.warn("GlobalExceptionHandler: '{}' not found in error catalog — using static fallback.",
                             internalCode);
                    return staticFallback(internalCode);
                });
    }

    /** Static fallback DTO when the catalog doesn't have the requested code. */
    private ErrorCatalogDto staticFallback(String internalCode) {
        return switch (internalCode) {
            case CODE_AUTH      -> dto(FALLBACK_AUTH_EXT_CODE,
                                       "Authentication failed. Invalid credentials.", 401, false);
            case CODE_FORBIDDEN -> dto(FALLBACK_FORBIDDEN_EXT_CODE,
                                       "Access denied. Insufficient permissions.", 403, false);
            case CODE_VALIDATION -> dto(FALLBACK_VALIDATION_EXT_CODE,
                                        "Invalid request.", 400, false);
            default             -> dto(FALLBACK_GENERIC_EXT_CODE,
                                       "An unexpected error occurred. Use correlationId to contact support.",
                                       500, false);
        };
    }

    /** Minimal DTO builder for static fallback values. */
    private ErrorCatalogDto dto(String externalCode, String message, int httpStatus, boolean retryable) {
        return new ErrorCatalogDto(null, externalCode, message, null,
                                   "SYSTEM", "CRITICAL", httpStatus, retryable);
    }

    private String newCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
