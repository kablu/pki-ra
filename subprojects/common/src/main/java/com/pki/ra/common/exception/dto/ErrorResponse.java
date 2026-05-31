package com.pki.ra.common.exception.dto;

import java.time.Instant;

/**
 * External-safe API error response returned by {@link com.pki.ra.common.web.GlobalExceptionHandler}.
 *
 * <h3>Security contract — what is and is NOT included</h3>
 * <table border="1">
 *   <tr><th>Field</th><th>Included?</th><th>Reason</th></tr>
 *   <tr><td>{@code correlationId}</td><td>✅ YES</td><td>UUID — no internal info, used for log cross-reference</td></tr>
 *   <tr><td>{@code errorCode}</td><td>✅ YES</td><td>External code only (e.g. ERR-001) — safe for client handling</td></tr>
 *   <tr><td>{@code message}</td><td>✅ YES</td><td>External sanitized message only</td></tr>
 *   <tr><td>{@code status}</td><td>✅ YES</td><td>HTTP status code</td></tr>
 *   <tr><td>{@code retryable}</td><td>✅ YES</td><td>Tells client if retry is safe</td></tr>
 *   <tr><td>{@code timestamp}</td><td>✅ YES</td><td>UTC timestamp of the error</td></tr>
 *   <tr><td>internalCode</td><td>❌ NO</td><td>Reveals internal system identifiers</td></tr>
 *   <tr><td>internalMessage / description</td><td>❌ NO</td><td>Contains technical details / stack hints</td></tr>
 *   <tr><td>stack trace</td><td>❌ NO</td><td>Reveals class names, file paths, line numbers</td></tr>
 *   <tr><td>cause message</td><td>❌ NO</td><td>May contain LDAP DN, SQL, host names</td></tr>
 * </table>
 *
 * @param correlationId UUID for cross-referencing this error in server logs
 * @param errorCode     External error code safe for client handling (e.g. {@code "ERR-001"})
 * @param message       External, sanitized, user-friendly error message
 * @param status        HTTP status code (mirrors the response HTTP status)
 * @param retryable     {@code true} if the client can safely retry the request
 * @param timestamp     UTC instant when the error occurred
 *
 * @author pki-ra
 * @since  1.0.0
 */
public record ErrorResponse(
        String  correlationId,
        String  errorCode,
        String  message,
        int     status,
        boolean retryable,
        Instant timestamp
) {

    /**
     * Convenience factory — timestamp defaults to {@link Instant#now()}.
     */
    public static ErrorResponse of(String correlationId, String errorCode,
                                   String message, int status, boolean retryable) {
        return new ErrorResponse(correlationId, errorCode, message, status, retryable, Instant.now());
    }
}
