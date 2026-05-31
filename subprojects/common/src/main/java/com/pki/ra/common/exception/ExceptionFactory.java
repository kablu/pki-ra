package com.pki.ra.common.exception;

import com.pki.ra.common.error.ErrorCatalogBean;
import com.pki.ra.common.error.ErrorCodeKey;
import com.pki.ra.common.error.dto.ErrorCatalogDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;

/**
 * Factory for creating {@link AppException} instances backed by the
 * {@link ErrorCatalogBean} in-memory cache.
 *
 * <h3>Why a factory — not direct constructor?</h3>
 * <ul>
 *   <li>Centralises error-catalog lookup — no caller imports the cache directly.</li>
 *   <li>Enforces the internal/external message separation everywhere.</li>
 *   <li>Supports {@link MessageFormat} placeholders ({@code {0}}, {@code {1}}, …)
 *       in both internal and external messages.</li>
 *   <li>Safe fallback when an error code is not in cache — never throws NPE.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Type-safe enum (preferred)
 * throw exceptionFactory.create(RaErrorCode.CERT_NOT_FOUND, serialNumber);
 *
 * // String code (dynamic)
 * throw exceptionFactory.create("PKI_CERT_001", serialNumber);
 *
 * // Wrapping a cause
 * throw exceptionFactory.create(RaErrorCode.CA_UNREACHABLE, cause, caHost);
 *
 * // Create + log in one call
 * throw exceptionFactory.createAndLog(log, RaErrorCode.CERT_NOT_FOUND, serialNumber);
 * }</pre>
 *
 * <h3>Fallback — catalog miss</h3>
 * If internalCode is not in cache, a generic SYSTEM error is returned.
 * External message is always safe — never reveals missing code to API callers.
 *
 * @see AppException
 * @see ErrorCatalogBean
 * @see ErrorCodeKey
 * @author pki-ra
 * @since  1.0.0
 */
@Component
public class ExceptionFactory {

    private static final Logger log = LoggerFactory.getLogger(ExceptionFactory.class);

    // Fallback values when internalCode is not found in catalog
    private static final String FALLBACK_EXTERNAL_CODE    = "ERR-999";
    private static final String FALLBACK_EXTERNAL_MESSAGE = "An unexpected error occurred. Please try again later.";
    private static final int    FALLBACK_HTTP_STATUS       = 500;

    private final ErrorCatalogBean errorCatalogBean;

    public ExceptionFactory(ErrorCatalogBean errorCatalogBean) {
        this.errorCatalogBean = errorCatalogBean;
    }

    // =========================================================================
    // create — from ErrorCodeKey (type-safe, preferred)
    // =========================================================================

    public AppException create(ErrorCodeKey key, Object... args) {
        return create(key.code(), args);
    }

    public AppException create(ErrorCodeKey key, Throwable cause, Object... args) {
        return create(key.code(), cause, args);
    }

    // =========================================================================
    // create — from String code
    // =========================================================================

    public AppException create(String internalCode, Object... args) {
        ErrorCatalogDto entry = resolve(internalCode);
        return new AppException(
                internalCode,
                entry.externalCode(),
                format(entry.description(), args),   // internal → logs only
                format(entry.message(), args),        // external → API response
                entry.httpStatus(),
                entry.isRetryable()
        );
    }

    public AppException create(String internalCode, Throwable cause, Object... args) {
        ErrorCatalogDto entry = resolve(internalCode);
        return new AppException(
                internalCode,
                entry.externalCode(),
                format(entry.description(), args),
                format(entry.message(), args),
                entry.httpStatus(),
                entry.isRetryable(),
                cause
        );
    }

    // =========================================================================
    // createAndLog — create + log internal details in one call
    // =========================================================================

    /**
     * Creates an {@link AppException} AND logs internal details at ERROR level.
     * Log contains: correlationId, internalCode, internal message.
     * Nothing from this log entry reaches the API response.
     */
    public AppException createAndLog(Logger logger, ErrorCodeKey key, Object... args) {
        return createAndLog(logger, key.code(), null, args);
    }

    public AppException createAndLog(Logger logger, ErrorCodeKey key, Throwable cause, Object... args) {
        return createAndLog(logger, key.code(), cause, args);
    }

    public AppException createAndLog(Logger logger, String internalCode,
                                     Throwable cause, Object... args) {
        AppException ex = (cause != null)
                ? create(internalCode, cause, args)
                : create(internalCode, args);

        if (cause != null) {
            logger.error("[{}] correlationId='{}' — {} | cause: {}",
                    ex.getInternalCode(), ex.getCorrelationId(),
                    ex.getMessage(), cause.getMessage(), cause);
        } else {
            logger.error("[{}] correlationId='{}' — {}",
                    ex.getInternalCode(), ex.getCorrelationId(), ex.getMessage());
        }

        return ex;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private ErrorCatalogDto resolve(String internalCode) {
        return errorCatalogBean.getByInternalCode(internalCode)
                .orElseGet(() -> {
                    log.warn("ExceptionFactory: internalCode '{}' not found in error catalog — using fallback.",
                             internalCode);
                    return fallback(internalCode);
                });
    }

    private ErrorCatalogDto fallback(String missingCode) {
        String internalMsg = "Error catalog miss — internalCode [" + missingCode + "] not found in cache.";
        return new ErrorCatalogDto(
                missingCode,
                FALLBACK_EXTERNAL_CODE,
                FALLBACK_EXTERNAL_MESSAGE,   // message = external (safe to expose)
                internalMsg,                 // description = internal (logs only)
                "SYSTEM",
                "CRITICAL",
                FALLBACK_HTTP_STATUS,
                false
        );
    }

    private String format(String pattern, Object[] args) {
        if (pattern == null || pattern.isBlank()) return "";
        if (args == null || args.length == 0)     return pattern;
        try {
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            log.warn("ExceptionFactory: failed to format message pattern '{}' — using raw.", pattern);
            return pattern;
        }
    }
}
