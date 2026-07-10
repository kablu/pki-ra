package com.pki.ra.common.config;

import com.pki.ra.common.config.dto.RefreshResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic base class for all hot-reloadable in-memory caches.
 *
 * <h3>What this eliminates across every implementing bean</h3>
 * <ul>
 *   <li>{@code @Value} injection of {@code spring.application.name}</li>
 *   <li>{@link #getServiceName()} implementation</li>
 *   <li>{@link ApplicationReadyEvent} listener ({@code @Order(10)})</li>
 *   <li>{@link #refresh(String)} body — clear → load → return {@link RefreshResult}</li>
 *   <li>Primary {@link ConcurrentHashMap} cache field</li>
 *   <li>{@link #getAll()}, {@link #size()} utility methods</li>
 * </ul>
 *
 * <h3>Subclass contract — 3 methods only</h3>
 * <ol>
 *   <li>{@link #entityClass()} — return the JPA entity class (drives {@code getResourceId()}
 *       and {@code getAuditAction()} automatically via {@link Refreshable} defaults).</li>
 *   <li>{@link #doLoad()} — clear and repopulate {@link #cache} (and any secondary
 *       indexes) from the repository. Called on startup AND on every hot-reload.</li>
 *   <li>{@link #printTable()} — log the freshly loaded cache contents in a
 *       formatted table. Called once at the end of every load.</li>
 * </ol>
 *
 * <h3>Secondary indexes</h3>
 * Subclasses that need more than one lookup axis (e.g. by both internalCode and
 * externalCode) declare and manage their own secondary {@link ConcurrentHashMap}
 * fields. The base class owns only the primary {@link #cache}.
 *
 * <h3>Thread safety</h3>
 * The primary cache is a {@link ConcurrentHashMap} — safe for concurrent reads.
 * A reload briefly sees a partially-empty cache while {@link #doLoad()} runs;
 * this is acceptable for configuration / catalog data. Subclasses must apply the
 * same policy to any secondary maps they own.
 *
 * <h3>Usage — minimum viable subclass</h3>
 * <pre>{@code
 * @Service
 * public class WidgetConfigBean extends AbstractRefreshableCache<WidgetConfig, WidgetConfigDto> {
 *
 *     private final WidgetConfigRepository repository;
 *
 *     public WidgetConfigBean(WidgetConfigRepository repository,
 *                             @Value("${spring.application.name}") String applicationName) {
 *         super(applicationName);
 *         this.repository = repository;
 *     }
 *
 *     @Override public Class<?> entityClass() { return WidgetConfig.class; }
 *
 *     @Override
 *     protected void doLoad() {
 *         cache.clear();
 *         repository.findAllActive().forEach(row ->
 *             cache.put(row.getKey(), toDto(row)));
 *     }
 *
 *     @Override
 *     protected void printTable() {
 *         cache.values().forEach(dto -> log.info("  {}", dto));
 *     }
 *
 *     public Optional<WidgetConfigDto> get(String key) {
 *         return Optional.ofNullable(cache.get(key));
 *     }
 * }
 * }</pre>
 *
 * @param <E>   JPA entity type backing the cache (e.g. {@code AppConfig})
 * @param <DTO> DTO type stored in the primary cache map (e.g. {@code AppConfigDto})
 *
 * @see Refreshable
 * @see com.pki.ra.common.web.AbstractRefreshController
 * @author pki-ra
 * @since  1.0.0
 */
public abstract class AbstractRefreshableCache<E, DTO> implements Refreshable {

    /** Logger scoped to the concrete subclass for meaningful log output. */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** Primary lookup cache — keyed by the entity's main identifier. */
    protected final ConcurrentHashMap<String, DTO> cache = new ConcurrentHashMap<>();

    private final String applicationName;

    /**
     * Constructor for subclasses.
     *
     * @param applicationName value of {@code ${spring.application.name}};
     *                        inject via {@code @Value} in the concrete bean's constructor
     *                        and pass through here
     */
    protected AbstractRefreshableCache(String applicationName) {
        this.applicationName = applicationName;
    }

    // =========================================================================
    // Refreshable — getServiceName (implemented once here)
    // =========================================================================

    /**
     * Returns {@code spring.application.name} — sourced from the value injected
     * into the concrete subclass's constructor and passed to {@link #AbstractRefreshableCache}.
     */
    @Override
    public final String getServiceName() {
        return applicationName;
    }

    // =========================================================================
    // Startup load — inherited by every subclass automatically
    // =========================================================================

    /**
     * Loads the cache from the database on application startup.
     *
     * <p>Fires on {@link ApplicationReadyEvent} (after the full Spring context
     * is ready, including DB connection and any seeders that ran as
     * {@link org.springframework.boot.ApplicationRunner}).
     *
     * <p>{@code @Order(10)} — runs early among ApplicationReadyEvent listeners;
     * adjust per-bean by overriding with a different {@code @Order} value if needed.
     *
     * <p>Marked {@code final} — the startup sequence (call {@link #doLoad()}, then
     * {@link #printTable()}) must not be altered by subclasses.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public final void loadOnReady() {
        doLoad();
        printTable();
    }

    // =========================================================================
    // Refreshable — refresh (template, implemented once here)
    // =========================================================================

    /**
     * Hot-reloads the cache without restarting the application.
     *
     * <p>Delegates data loading to {@link #doLoad()}, then logs the outcome
     * and returns a fully-populated {@link RefreshResult}. The audit-log entry
     * is written by the caller ({@link com.pki.ra.common.web.AbstractRefreshController}),
     * not here.
     *
     * @param triggeredBy username of the admin who triggered the reload,
     *                    or {@code "system"} for scheduler-initiated reloads
     * @return populated {@link RefreshResult} — never {@code null}
     */
    @Override
    public final RefreshResult refresh(String triggeredBy) {
        log.info("{}: refresh triggered by '{}'", getClass().getSimpleName(), triggeredBy);
        doLoad();
        printTable();
        log.info("{}: refresh complete — {} entries loaded.",
                 getClass().getSimpleName(), cache.size());
        return new RefreshResult(
                getServiceName(),
                getResourceId(),
                cache.size(),
                Instant.now(),
                triggeredBy
        );
    }

    // =========================================================================
    // Subclass contract — must implement
    // =========================================================================

    /**
     * Clears and repopulates the primary {@link #cache} (and any secondary indexes
     * owned by the subclass) from the underlying repository.
     *
     * <p>Called on startup ({@link #loadOnReady()}) and on every hot-reload
     * ({@link #refresh(String)}).
     *
     * <p>Implementation must always start with {@code cache.clear()} before
     * inserting new entries to avoid stale data after a reload.
     */
    protected abstract void doLoad();

    /**
     * Logs the current contents of the cache in a human-readable table format.
     *
     * <p>Called once at the end of every successful load or reload.
     * Implementations should log at {@code INFO} level and handle the empty-cache
     * case with a {@code WARN} line.
     */
    protected abstract void printTable();

    // =========================================================================
    // Shared utility methods — available to all subclasses
    // =========================================================================

    /**
     * Returns an unmodifiable view of the primary cache.
     *
     * @return read-only map of {@code key → DTO}
     */
    public Map<String, DTO> getAll() {
        return Collections.unmodifiableMap(cache);
    }

    /**
     * Returns the number of entries currently in the primary cache.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Returns {@code true} if the primary cache contains an entry for the given key.
     */
    public boolean containsKey(String key) {
        return cache.containsKey(key);
    }
}
