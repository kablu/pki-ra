package com.pki.ra.common.error;

import com.pki.ra.common.error.dto.ErrorCatalogDto;

import java.util.Optional;

/**
 * Read-only abstraction over the error catalog cache.
 *
 * <h3>Why an interface?</h3>
 * <ul>
 *   <li><b>Decoupling</b> — {@link com.pki.ra.common.exception.ExceptionFactory} and
 *       {@link com.pki.ra.common.web.AbstractGlobalExceptionHandler} depend on this
 *       interface, not on the concrete {@link ErrorCatalogBean}. No class in the
 *       exception / web layer ever imports a cache implementation.</li>
 *   <li><b>Testability</b> — unit tests mock this interface with two lines;
 *       no Spring context, no H2 DB, no seeder needed.</li>
 *   <li><b>Future-proofing</b> — alternative implementations (properties file,
 *       remote config server, per-module overlay) can be plugged in without
 *       touching callers.</li>
 * </ul>
 *
 * <h3>Known implementations</h3>
 * <ul>
 *   <li>{@link ErrorCatalogBean} — DB-backed in-memory cache (production)</li>
 * </ul>
 *
 * <h3>Callers</h3>
 * <ul>
 *   <li>{@link com.pki.ra.common.exception.ExceptionFactory} — looks up entries
 *       by internal code to build {@link com.pki.ra.common.exception.AppException}.</li>
 *   <li>{@link com.pki.ra.common.web.AbstractGlobalExceptionHandler} — looks up
 *       fallback entries by internal code to build catalog-driven error responses
 *       for non-{@code AppException} handlers.</li>
 *   <li>{@link ErrorCatalogStartupValidator} — checks that every registered
 *       {@link ErrorCodeKey} value exists in the cache.</li>
 * </ul>
 *
 * @see ErrorCatalogBean
 * @see ErrorCatalogDto
 * @author pki-ra
 * @since  1.0.0
 */
public interface ErrorCatalogProvider {

    /**
     * Looks up an error entry by its internal code.
     *
     * @param internalCode the {@code internal_code} value (e.g. {@code "PKI_CERT_001"})
     * @return the matching {@link ErrorCatalogDto}, or {@link Optional#empty()} if
     *         the code is not in the catalog (not loaded yet, or row absent from DB)
     */
    Optional<ErrorCatalogDto> getByInternalCode(String internalCode);

    /**
     * Returns {@code true} if the cache contains an entry for the given internal code.
     * Convenience for validation logic.
     *
     * @param internalCode the {@code internal_code} value to check
     */
    boolean containsInternalCode(String internalCode);
}
