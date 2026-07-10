package com.pki.ra.common.config;

import com.pki.ra.common.config.dto.AppConfigDto;
import com.pki.ra.common.model.AppConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * In-memory cache of all active {@code app_config} rows.
 *
 * <p>Loaded once on {@link org.springframework.boot.context.event.ApplicationReadyEvent}
 * — after the DB is seeded and the full Spring context is ready.
 * Key = {@code config_key}, value = {@link AppConfigDto}.
 * No DB calls on the hot path after startup.
 *
 * <h3>Extending {@link AbstractRefreshableCache}</h3>
 * The following are fully inherited — zero duplication here:
 * <ul>
 *   <li>{@code applicationName} field + {@link #getServiceName()} method</li>
 *   <li>{@code loadOnReady()} — {@code @EventListener(ApplicationReadyEvent)} startup trigger</li>
 *   <li>{@code refresh(String triggeredBy)} — hot-reload body + {@link com.pki.ra.common.config.dto.RefreshResult}</li>
 *   <li>Primary {@code cache} ({@link java.util.concurrent.ConcurrentHashMap})</li>
 *   <li>{@link #getAll()}, {@link #size()}, {@link #containsKey(String)}</li>
 * </ul>
 * This class only implements {@link #entityClass()}, {@link #doLoad()},
 * {@link #printTable()}, and the public read API.
 *
 * <h3>Refreshable metadata — zero hardcoding</h3>
 * <pre>
 *  getServiceName()  ← "${spring.application.name}"  (via base class)
 *  entityClass()     ← AppConfig.class               (single line)
 *  getResourceId()   ← "app_config"                  (auto from @Table)
 *  getAuditAction()  ← "APP_CONFIG_REFRESH"           (auto by convention)
 * </pre>
 *
 * <p>Usage:
 * <pre>{@code
 * String host = configBean.getValue("host").orElseThrow();
 * AppConfigDto row = configBean.get("host").orElseThrow();
 * }</pre>
 *
 * @see AbstractRefreshableCache
 * @see Refreshable
 * @see com.pki.ra.common.web.AbstractRefreshController
 */
@Service
public class ConfigBean extends AbstractRefreshableCache<AppConfig, AppConfigDto> {

    private final AppConfigRepository repository;

    public ConfigBean(AppConfigRepository repository,
                      @Value("${spring.application.name}") String applicationName) {
        super(applicationName);
        this.repository = repository;
    }

    // =========================================================================
    // Refreshable — must implement (2 methods only)
    // =========================================================================

    /**
     * Returns {@link AppConfig}{@code .class} — the JPA entity backing this cache.
     *
     * <p>Drives automatic derivation of:
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

    // =========================================================================
    // AbstractRefreshableCache — must implement (2 methods only)
    // =========================================================================

    /**
     * Clears and repopulates the cache from {@code app_config} active rows.
     * Called on startup and on every hot-reload — no audit side-effects.
     */
    @Override
    protected void doLoad() {
        cache.clear();
        repository.findAllActive().forEach(row ->
                cache.put(row.getConfigKey(), new AppConfigDto(
                        row.getConfigKey(),
                        row.getConfigType(),
                        row.getConfigValue(),
                        row.isActive()
                ))
        );
    }

    @Override
    protected void printTable() {
        if (cache.isEmpty()) {
            log.warn("ConfigBean: app_config table has no active rows.");
            return;
        }

        int keyW  = cache.values().stream().mapToInt(r -> r.configKey().length()).max().orElse(10);
        int typeW = cache.values().stream().mapToInt(r -> r.configType().length()).max().orElse(6);
        int valW  = cache.values().stream().mapToInt(r -> r.configValue().length()).max().orElse(10);

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

    // =========================================================================
    // Cache read API — ConfigBean-specific lookups
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

    // getAll(), size(), containsKey() — inherited from AbstractRefreshableCache
}
