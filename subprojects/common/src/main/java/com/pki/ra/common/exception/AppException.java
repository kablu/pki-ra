package com.pki.ra.common.exception;

import java.util.UUID;

/**
 * Central runtime exception for all PKI-RA application errors.
 *
 * <h3>Internal vs External separation — Security first</h3>
 * <ul>
 *   <li><b>Internal</b> — {@link #getMessage()} (from {@code error_catalog.description})
 *       contains full technical details. Written to logs ONLY. Never exposed via API.</li>
 *   <li><b>External</b> — {@link #getExternalMessage()} (from {@code error_catalog.message})
 *       is a sanitized, user-friendly message. Safe to return in API responses.</li>
 *   <li><b>Internal code</b> — {@link #getInternalCode()} (e.g. {@code "PKI_CERT_001"})
 *       identifies the error internally. Never exposed via API.</li>
 *   <li><b>External code</b> — {@link #getExternalCode()} (e.g. {@code "ERR-001"})
 *       is safe to include in API response for client-side error handling.</li>
 * </ul>
 *
 * <h3>Correlation ID</h3>
 * Every instance carries a UUID {@link #correlationId} generated at throw time.
 * Include this in both the log entry and the API response — operations teams can
 * cross-reference without exposing internal details to attackers.
 *
 * <h3>Message placeholders</h3>
 * Both internal and external messages support {@code java.text.MessageFormat}
 * placeholders ({@code {0}}, {@code {1}}, …). Pass args to
 * {@link ExceptionFactory#create(String, Object...)} — formatting happens there.
 *
 * <h3>Never instantiate directly</h3>
 * Always use {@link ExceptionFactory#create(String, Object...)} or
 * {@link ExceptionFactory#create(ErrorCodeKey, Object...)} so the messages
 * are always sourced from the cached error catalog.
 *
 * @see ExceptionFactory
 * @see ErrorCodeKey
 * @author pki-ra
 * @since  1.0.0
 */
public class AppException extends RuntimeException {

    /** Unique ID for cross-referencing logs and API response. Never leaks internal data. */
    private final String correlationId;

    /** Internal error code — e.g. {@code "PKI_CERT_001"}. NEVER in API response. */
    private final String internalCode;

    /** External error code — e.g. {@code "ERR-001"}. Safe for API response. */
    private final String externalCode;

    /**
     * External, sanitized message for API response.
     * {@link #getMessage()} returns the INTERNAL message (for logs only).
     */
    private final String externalMessage;

    /** HTTP status code to use in the response. */
    private final int httpStatus;

    /** Whether the client can safely retry the failed operation. */
    private final boolean retryable;

    /**
     * Package-private constructor — use {@link ExceptionFactory} to create instances.
     *
     * @param internalCode    code from {@code error_catalog.internal_code}
     * @param externalCode    code from {@code error_catalog.external_code}
     * @param internalMessage formatted internal message (→ logs only, sets super message)
     * @param externalMessage formatted external message (→ API response)
     * @param httpStatus      HTTP status code
     * @param retryable       whether the client can retry
     */
    AppException(String internalCode,
                 String externalCode,
                 String internalMessage,
                 String externalMessage,
                 int    httpStatus,
                 boolean retryable) {
        super(internalMessage);   // ← getMessage() = internal, written to logs only
        this.correlationId   = UUID.randomUUID().toString();
        this.internalCode    = internalCode;
        this.externalCode    = externalCode;
        this.externalMessage = externalMessage;
        this.httpStatus      = httpStatus;
        this.retryable       = retryable;
    }

    /**
     * Package-private constructor with cause — use {@link ExceptionFactory}.
     */
    AppException(String internalCode,
                 String externalCode,
                 String internalMessage,
                 String externalMessage,
                 int    httpStatus,
                 boolean retryable,
                 Throwable cause) {
        super(internalMessage, cause);
        this.correlationId   = UUID.randomUUID().toString();
        this.internalCode    = internalCode;
        this.externalCode    = externalCode;
        this.externalMessage = externalMessage;
        this.httpStatus      = httpStatus;
        this.retryable       = retryable;
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /**
     * UUID for correlating logs with API responses.
     * Safe to include in API error response.
     */
    public String getCorrelationId()   { return correlationId;   }

    /**
     * Internal code — e.g. {@code "PKI_CERT_001"}.
     * <strong>NEVER include in API response.</strong> Use in log statements only.
     */
    public String getInternalCode()    { return internalCode;    }

    /**
     * External code — e.g. {@code "ERR-001"}.
     * Safe to include in API response for client-side error handling.
     */
    public String getExternalCode()    { return externalCode;    }

    /**
     * Sanitized external message safe for API response.
     * {@link #getMessage()} returns the INTERNAL message — for logs only.
     */
    public String getExternalMessage() { return externalMessage; }

    /** HTTP status code to use in the response. */
    public int    getHttpStatus()      { return httpStatus;      }

    /** Whether the client can safely retry the failed operation. */
    public boolean isRetryable()       { return retryable;       }

    /**
     * Returns a log-friendly string including correlation ID and internal code.
     * Safe for log appenders — contains NO external-facing content.
     */
    @Override
    public String toString() {
        return "AppException{correlationId='" + correlationId
                + "', internalCode='" + internalCode
                + "', httpStatus=" + httpStatus
                + ", message='" + getMessage() + "'}";
    }
}
