package com.pki.ra.common.config;

import com.pki.ra.common.config.dto.RefreshResult;
import jakarta.persistence.Table;

/**
 * Marks a Spring bean as a hot-reloadable in-memory cache.
 *
 * <h3>Design Pattern — Strategy + Template Method</h3>
 * Each service that holds a config cache (RA, CMP, ACME, Online) implements
 * this interface. {@link com.pki.ra.common.web.AbstractRefreshController}
 * depends on this abstraction — no controller ever imports a concrete bean.
 *
 * <h3>Zero hardcoding — how each metadata value is sourced</h3>
 * <pre>
 *  getServiceName()  ← spring.application.name    injected via @Value
 *  entityClass()     ← JPA entity class            single line, you provide this
 *  getResourceId()   ← @Table(name = "...")        auto-read from entityClass()
 *  getAuditAction()  ← resourceId + "_REFRESH"    auto-derived by convention
 * </pre>
 *
 * <h3>Implementing class must provide only 2 methods</h3>
 * <ol>
 *   <li>{@link #getServiceName()} — inject {@code spring.application.name} via
 *       {@code @Value} and return it</li>
 *   <li>{@link #entityClass()} — return the JPA entity class that backs this cache</li>
 * </ol>
 * {@link #getResourceId()} and {@link #getAuditAction()} are derived automatically
 * via {@code default} methods — <strong>never override them</strong>.
 *
 * <h3>Adding a new service refresh — full checklist</h3>
 * <ol>
 *   <li>Implement this interface on the cache bean — provide 2 methods above.</li>
 *   <li>Extend {@link com.pki.ra.common.web.AbstractRefreshController}
 *       — override {@code refreshableService()} only.</li>
 *   <li>Done — audit log, IP extraction, error handling all inherited.</li>
 * </ol>
 *
 * <h3>Known implementations</h3>
 * <ul>
 *   <li>{@link ConfigBean}   — RA service, {@code app_config} table</li>
 *   <li>CmpConfigBean        — CMP service (future)</li>
 *   <li>AcmeConfigBean       — ACME service (future)</li>
 *   <li>OnlineConfigBean     — Online service (future)</li>
 * </ul>
 *
 * @see RefreshResult
 * @see com.pki.ra.common.web.AbstractRefreshController
 */
public interface Refreshable {

    // =========================================================================
    // Core operation
    // =========================================================================

    /**
     * Hot-reloads this bean's in-memory cache from the underlying data source.
     *
     * <p>Implementation contract:
     * <ol>
     *   <li>Clear the existing cache completely before reloading.</li>
     *   <li>Reload all active entries from the database.</li>
     *   <li>Be thread-safe — use {@link java.util.concurrent.ConcurrentHashMap}.</li>
     *   <li>Return a fully populated {@link RefreshResult} — never {@code null}.</li>
     * </ol>
     *
     * <p>The caller ({@link com.pki.ra.common.web.AbstractRefreshController})
     * writes the audit log entry. This method only reloads and reports —
     * no audit side-effects inside the implementation.
     *
     * @param triggeredBy username of the actor (e.g. {@code "admin"})
     *                    or {@code "system"} for scheduler calls
     * @return populated {@link RefreshResult} — never {@code null}
     */
    RefreshResult refresh(String triggeredBy);

    /**
     * Convenience overload for system / scheduler triggers.
     * Delegates to {@link #refresh(String)} with {@code "system"}.
     * Implementations do <strong>not</strong> need to override this.
     */
    default RefreshResult refresh() {
        return refresh("system");
    }

    // =========================================================================
    // Must implement — 2 methods only
    // =========================================================================

    /**
     * Returns the module name sourced from {@code spring.application.name}.
     *
     * <p>Inject via {@code @Value("${spring.application.name}")} in the
     * implementing bean's constructor — <strong>never hardcode this value</strong>.
     *
     * <p>Example:
     * <pre>{@code
     * private final String applicationName;
     *
     * public ConfigBean(
     *         AppConfigRepository repo,
     *         @Value("${spring.application.name}") String applicationName) {
     *     this.applicationName = applicationName;
     * }
     *
     * @Override
     * public String getServiceName() { return applicationName; }
     * // returns "pki-ra-raservice" from application.yml automatically
     * }</pre>
     *
     * @return value of {@code spring.application.name} — never null
     */
    String getServiceName();

    /**
     * Returns the JPA entity class that backs this cache.
     *
     * <p>The {@link Refreshable} interface reads the {@code @Table} annotation
     * from this class to derive {@link #getResourceId()} and
     * {@link #getAuditAction()} automatically — no hardcoding needed.
     *
     * <p>Example:
     * <pre>{@code
     * @Override
     * public Class<?> entityClass() { return AppConfig.class; }
     * // → @Table(name = "app_config")
     * // → getResourceId()  = "app_config"         (auto)
     * // → getAuditAction() = "APP_CONFIG_REFRESH"  (auto)
     * }</pre>
     *
     * @return JPA entity class — never null
     */
    Class<?> entityClass();

    // =========================================================================
    // Auto-derived — default implementations, do NOT override
    // =========================================================================

    /**
     * Returns the DB table name from the {@code @Table} annotation on
     * {@link #entityClass()} — written to {@code audit_log.resource_id}.
     *
     * <h3>Resolution order</h3>
     * <ol>
     *   <li>{@code @Table(name = "...")} on {@link #entityClass()} — first choice.</li>
     *   <li>Class simple name converted to snake_case — fallback if annotation
     *       is absent or name is blank.</li>
     * </ol>
     *
     * <p><strong>Do not override.</strong> The entity's {@code @Table}
     * annotation is the single source of truth.
     *
     * <p>Examples:
     * <pre>
     *   AppConfig  → @Table(name = "app_config") → "app_config"
     *   CmpConfig  → @Table(name = "cmp_config") → "cmp_config"
     * </pre>
     */
    default String getResourceId() {
        Table table = entityClass().getAnnotation(Table.class);
        if (table != null && !table.name().isBlank()) {
            return table.name();
        }
        return toSnakeCase(entityClass().getSimpleName());
    }

    /**
     * Returns the audit action constant — derived from {@link #getResourceId()}
     * by uppercasing and appending {@code "_REFRESH"}.
     *
     * <p><strong>Do not override</strong> unless the convention does not fit.
     *
     * <p>Examples:
     * <pre>
     *   "app_config"  → "APP_CONFIG_REFRESH"
     *   "cmp_config"  → "CMP_CONFIG_REFRESH"
     *   "acme_config" → "ACME_CONFIG_REFRESH"
     * </pre>
     */
    default String getAuditAction() {
        return getResourceId().toUpperCase() + "_REFRESH";
    }

    // =========================================================================
    // Private helper
    // =========================================================================

    /**
     * Converts a camelCase class name to snake_case.
     * Used as fallback when {@code @Table} annotation is absent or has no name.
     *
     * <p>Examples:
     * <pre>
     *   AppConfig  → app_config
     *   CmpConfig  → cmp_config
     * </pre>
     */
    private static String toSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
