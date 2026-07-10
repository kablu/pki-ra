package com.pki.ra.common.error;

/**
 * Marker interface for type-safe error code enums across all PKI-RA modules.
 *
 * <h3>Why an interface — not a single shared enum?</h3>
 * Each module (raservice, caservice, scheduler) owns its own domain error codes.
 * A single enum in {@code common} would couple all modules — a change in one
 * module would require a common-module rebuild for all.
 * Instead, each module defines its own enum implementing this interface:
 *
 * <pre>{@code
 * // raservice module
 * public enum RaErrorCode implements ErrorCodeKey {
 *     CERT_NOT_FOUND    ("PKI_CERT_001"),
 *     CERT_REVOKED      ("PKI_CERT_004"),
 *     AUTH_FAILED       ("PKI_AUTH_001");
 *
 *     private final String code;
 *     RaErrorCode(String code) { this.code = code; }
 *
 *     @Override public String code() { return code; }
 * }
 * }</pre>
 *
 * <h3>Usage with ExceptionFactory</h3>
 * <pre>{@code
 * // Type-safe — IDE autocomplete, compile-time check
 * throw exceptionFactory.create(RaErrorCode.CERT_NOT_FOUND, serialNumber);
 *
 * // Or with String — for dynamic / runtime codes
 * throw exceptionFactory.create("PKI_CERT_001", serialNumber);
 * }</pre>
 *
 * <h3>Contract</h3>
 * {@link #code()} must return the exact {@code internal_code} value stored
 * in the {@code error_catalog} table. The {@link ExceptionFactory} uses it
 * to look up messages, HTTP status, and external code from the cache.
 *
 * @see ExceptionFactory
 * @see AppException
 * @author pki-ra
 * @since  1.0.0
 */
public interface ErrorCodeKey {

    /**
     * Returns the internal error code as stored in {@code error_catalog.internal_code}.
     * Example: {@code "PKI_CERT_001"}, {@code "PKI_AUTH_002"}.
     *
     * @return internal code string — never null or blank
     */
    String code();
}
