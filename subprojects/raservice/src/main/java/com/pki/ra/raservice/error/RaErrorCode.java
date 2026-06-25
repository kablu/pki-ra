package com.pki.ra.raservice.error;

import com.pki.ra.common.error.ErrorCodeKey;

/**
 * Type-safe error code constants for the RA service.
 *
 * <p>Each constant maps to a row in the {@code error_catalog} table via its
 * {@link #code()} value (= {@code internal_code} column).
 *
 * <h3>Usage — throw via ExceptionFactory (never instantiate AppException directly)</h3>
 * <pre>{@code
 * // Simple throw
 * throw exceptionFactory.create(RaErrorCode.CERT_NOT_FOUND, serialNumber);
 *
 * // Wrap a cause
 * throw exceptionFactory.create(RaErrorCode.CA_UNREACHABLE, cause, caHost);
 *
 * // Create + log in one call
 * throw exceptionFactory.createAndLog(log, RaErrorCode.CERT_NOT_FOUND, serialNumber);
 * }</pre>
 *
 * <h3>Startup validation</h3>
 * Every constant here is validated at startup by
 * {@link com.pki.ra.common.error.ErrorCatalogStartupValidator} against the live
 * {@code error_catalog} cache. Missing rows are logged as WARN so gaps are caught
 * at deployment time, not at the first API call that triggers the error.
 *
 * <h3>Adding a new error code</h3>
 * <ol>
 *   <li>Add a new constant below with the correct {@code internal_code} string.</li>
 *   <li>Insert the matching row into {@code error_catalog} (Flyway migration or
 *       direct DB insert + hot-reload via {@code POST /api/admin/error-catalog/refresh}).</li>
 *   <li>The startup validator will confirm both sides match at next restart.</li>
 * </ol>
 *
 * @see com.pki.ra.common.error.ErrorCodeKey
 * @see com.pki.ra.common.exception.ExceptionFactory
 * @author pki-ra
 * @since  1.0.0
 */
public enum RaErrorCode implements ErrorCodeKey {

    // ── Certificate ───────────────────────────────────────────────────────────
    CERT_NOT_FOUND       ("PKI_CERT_001"),
    CERT_EXPIRED         ("PKI_CERT_002"),
    CERT_REVOCATION_FAIL ("PKI_CERT_003"),
    CERT_ALREADY_REVOKED ("PKI_CERT_004"),
    CERT_GENERATION_FAIL ("PKI_CERT_005"),

    // ── Authentication / Authorization ────────────────────────────────────────
    AUTH_FAILED          ("PKI_AUTH_001"),
    ACCESS_DENIED        ("PKI_AUTH_002"),
    SESSION_EXPIRED      ("PKI_AUTH_003"),

    // ── Validation ───────────────────────────────────────────────────────────
    VALIDATION_REQUIRED_FIELD  ("PKI_VAL_001"),
    VALIDATION_INVALID_CSR     ("PKI_VAL_002"),

    // ── Network ──────────────────────────────────────────────────────────────
    CA_UNREACHABLE       ("PKI_NET_001"),
    LDAP_UNREACHABLE     ("PKI_NET_002"),

    // ── Approval Workflow ──────────────────────────────────────────────────
    APR_INVALID_STATUS         ("PKI_APR_001"),
    APR_SELF_PROCESS           ("PKI_APR_002"),
    APR_ADMIN_ONLY             ("PKI_APR_003"),
    APR_NOT_ASSIGNED           ("PKI_APR_004"),
    APR_MAKER_IS_CHECKER       ("PKI_APR_005"),
    APR_REQUESTOR_IS_OPERATOR  ("PKI_APR_006"),
    APR_OPERATOR_NOT_FOUND     ("PKI_APR_007"),
    APR_REMARKS_REQUIRED       ("PKI_APR_008"),
    APR_ALREADY_PICKED_UP      ("PKI_APR_009"),
    APR_RETURN_REASON_REQUIRED ("PKI_APR_010"),
    APR_INVALID_CONFIG         ("PKI_APR_011"),
    APR_MODE_BLOCKED           ("PKI_APR_012"),
    APR_MAX_PENDING_REACHED    ("PKI_APR_013"),

    // ── System ───────────────────────────────────────────────────────────────
    SYSTEM_ERROR         ("PKI_SYS_001"),
    SERVICE_UNAVAILABLE  ("PKI_SYS_002");

    private final String code;

    RaErrorCode(String code) {
        this.code = code;
    }

    /** Returns the {@code internal_code} value stored in {@code error_catalog}. */
    @Override
    public String code() {
        return code;
    }
}
