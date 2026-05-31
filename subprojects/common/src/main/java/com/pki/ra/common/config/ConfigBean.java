package com.pki.ra.common.config;

import com.pki.ra.common.config.dto.AppConfigDto;
import com.pki.ra.common.config.dto.RefreshResult;
import com.pki.ra.common.model.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of all active {@code app_config} rows.
 *
 * <p>Loaded once on {@link ApplicationReadyEvent} — after the DB is seeded
 * and the full Spring context is ready. Key = {@code config_key}, value =
 * {@link AppConfigDto}. No DB calls on the hot path after startup.
 *
 * <h3>Refreshable implementation — zero hardcoding</h3>
 * <pre>
 *  getServiceName()  ← "${spring.application.name}"  injected via @Value
 *  entityClass()     ← AppConfig.class                single line
 *  getResourceId()   ← "app_config"                   auto from @Table annotation
 *  getAuditAction()  ← "APP_CONFIG_REFRESH"            auto from convention
 * </pre>
 * Only {@link #getServiceName()} and {@link #entityClass()} are implemented here.
 * {@code getResourceId()} and {@code getAuditAction()} are inherited defaults
 * from {@link Refreshable} — nothing to hardcode, nothing to maintain.
 *
 * <h3>Thread safety</h3>
 * The cache is a {@link ConcurrentHashMap} — safe for concurrent reads.
 * {@link #loadOnReady()} clears and repopulates atomically per entry;
 * reads during a reload may briefly see a partially-empty cache,
 * which is acceptable for config data.
 *
 * <p>Usage:
 * <pre>{@code
 * String host = configBean.getValue("host").orElseThrow();
 * AppConfigDto row = configBean.get("host").orElseThrow();
 * }</pre>
 *
 * @see Refreshable
 * @see com.pki.ra.common.web.AbstractRefreshController
 */
@Service
public class ConfigBean implements Refreshable {

    private static final Logger log = LoggerFactory.getLogger(ConfigBean.class);

    private final AppConfigRepository repository;
    private final String              applicationName;

    private final ConcurrentHashMap<String, AppConfigDto> cache = new ConcurrentHashMap<>();

    public ConfigBean(AppConfigRepository repository,
                      @Value("${spring.application.name}") String applicationName) {
        this.repository      = repository;
        this.applicationName = applicationName;
    }

    // =========================================================================
    // Refreshable — must implement (2 methods only)
    // =========================================================================

    /**
     * Returns the module name from {@code spring.application.name}.
     *
     * <p>Automatically set per module via {@code application.yml} — no hardcoding.
     * Example: {@code "pki-ra-raservice"}
     */
    @Override
    public String getServiceName() {
        return applicationName;
    }

    /**
     * Returns {@link AppConfig}{@code .class} — the JPA entity backing this cache.
     *
     * <p>The {@link Refreshable} interface reads the {@code @Table} annotation
     * from this class automatically:
     * <pre>
     *   AppConfig → @Table(name = "app_config")
     *            → getResourceId()  = "app_config"         (auto)
     *            → getAuditAction() = "APP_CONFIG_REFRESH"  (auto)
     * </pre>
     */
    @Override
    public Class<?> entityClass() {
        return AppConfig.class;
    }

    // getResourceId()  — inherited: reads @Table(name="app_config") → "app_config"
    // getAuditAction() — inherited: "APP_CONFIG_REFRESH" (convention)

    // =========================================================================
    // Startup load
    // =========================================================================

    /**
     * Loads all active rows from {@code app_config} into the cache.
     * Runs automatically once on startup. Also called on every hot-reload.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void loadOnReady() {
        cache.clear();

        repository.findAllActive().forEach(row ->
                cache.put(row.getConfigKey(), new AppConfigDto(
                        row.getConfigKey(),
                        row.getConfigType(),
                        row.getConfigValue(),
                        row.isActive()
                ))
        );

        printTable();
    }

    // =========================================================================
    // Refreshable — core operation
    // =========================================================================

    /**
     * Hot-reloads the cache and returns a {@link RefreshResult}.
     *
     * <p>The caller ({@link com.pki.ra.common.web.AbstractRefreshController})
     * writes the audit log entry. This method only reloads and reports —
     * no audit side-effects here.
     *
     * @param triggeredBy username of the admin or {@code "system"}
     * @return populated {@link RefreshResult} — never null
     */
    @Override
    public RefreshResult refresh(String triggeredBy) {
        log.info("ConfigBean: manual refresh triggered by '{}'", triggeredBy);
        loadOnReady();
        return new RefreshResult(
                getServiceName(),    // "pki-ra-raservice" — from spring.application.name
                getResourceId(),     // "app_config"       — from @Table annotation (auto)
                cache.size(),
                Instant.now(),
                triggeredBy
        );
    }

    // =========================================================================
    // Cache read API
    // =========================================================================

    /**
     * Returns the full config row for the given key.
     *
     * @param key the {@code config_key} to look up
     * @return {@link Optional} of the row, or empty if not found
     */
    public Optional<AppConfigDto> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    /**
     * Convenience: returns just the {@code config_value} string.
     *
     * @param key the {@code config_key} to look up
     * @return {@link Optional} of the value string, or empty if not found
     */
    public Optional<String> getValue(String key) {
        return get(key).map(AppConfigDto::configValue);
    }

    /**
     * Returns an unmodifiable view of the full cache.
     *
     * @return unmodifiable map of {@code config_key → AppConfigDto}
     */
    public Map<String, AppConfigDto> getAll() {
        return Collections.unmodifiableMap(cache);
    }

    /**
     * Returns {@code true} if the cache contains an entry for the given key.
     */
    public boolean containsKey(String key) {
        return cache.containsKey(key);
    }

    /**
     * Returns the number of active entries currently in the cache.
     */
    public int size() {
        return cache.size();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void printTable() {
        if (cache.isEmpty()) {
            log.warn("ConfigBean: app_config table has no active rows.");
            return;
        }

        int keyW  = cache.values().stream()
                .mapToInt(r -> r.configKey().length()).max().orElse(10);
        int typeW = cache.values().stream()
                .mapToInt(r -> r.configType().length()).max().orElse(6);
        int valW  = cache.values().stream()
                .mapToInt(r -> r.configValue().length()).max().orElse(10);

        keyW  = Math.max(keyW,  10);
        typeW = Math.max(typeW,  4);
        valW  = Math.max(valW,   5);

        String fmt     = "| %-" + keyW  + "s | %-" + typeW + "s | %-" + valW + "s |";
        String divider = "+" + "-".repeat(keyW + 2)
                       + "+" + "-".repeat(typeW + 2)
                       + "+" + "-".repeat(valW + 2) + "+";

        log.info("ConfigBean: {} active config entries loaded", cache.size());
        log.info(divider);
        log.info(String.format(fmt, "config_key", "type", "value"));
        log.info(divider);
        cache.values().stream()
                .sorted((a, b) -> {
                    int cmp = a.configType().compareTo(b.configType());
                    return cmp != 0 ? cmp : a.configKey().compareTo(b.configKey());
                })
                .forEach(r -> log.info(String.format(fmt,
                        r.configKey(), r.configType(), r.configValue())));
        log.info(divider);
    }
}
