package com.pki.ra.common.error;

/**
 * Supplies a module's set of {@link ErrorCodeKey} values to the
 * {@link ErrorCatalogStartupValidator}.
 *
 * <h3>Why a separate interface — not a plain {@code @Bean List<ErrorCodeKey>}?</h3>
 * Enum constants cannot be Spring beans. This interface bridges the gap: each module
 * declares one {@code @Bean} implementing {@code ErrorCodeKeySource} that returns
 * its enum's {@link Enum#values()} array, and the validator collects all such beans
 * via {@code @Autowired List<ErrorCodeKeySource>}.
 *
 * <h3>Registration — one @Bean per module</h3>
 * <pre>{@code
 * // In any @Configuration class of the module:
 * @Bean
 * public ErrorCodeKeySource raErrorCodeKeySource() {
 *     return RaErrorCode::values;   // method reference to the enum's values()
 * }
 * }</pre>
 *
 * <h3>What happens with it</h3>
 * {@link ErrorCatalogStartupValidator} collects all registered sources and checks
 * every code against the {@link ErrorCatalogProvider} cache at startup.
 * A {@code WARN} log is emitted for any code that has no matching DB row —
 * so catalog gaps are caught at deployment time, not at first API call.
 *
 * @see ErrorCatalogStartupValidator
 * @see ErrorCodeKey
 * @author pki-ra
 * @since  1.0.0
 */
@FunctionalInterface
public interface ErrorCodeKeySource {

    /**
     * Returns all error code keys managed by this source.
     * Typically a direct reference to an enum's {@code values()} method.
     *
     * @return array of error code keys — never null, may be empty
     */
    ErrorCodeKey[] getAllCodes();
}
