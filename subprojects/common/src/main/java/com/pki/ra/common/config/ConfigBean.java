package com.pki.ra.common.config;

import com.pki.ra.common.config.dto.AppConfigDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of all active {@code app_config} rows.
 *
 * <p>Loaded once on {@link ApplicationReadyEvent} — after the DB is seeded
 * and the full Spring context is ready. Key = {@code config_key}, value = full
 * row as {@link AppConfigDto}. No DB calls on the request path after startup.
 *
 * <p>Usage:
 * <pre>{@code
 * String host = configBean.getValue("host").orElseThrow();
 * AppConfigDto row = configBean.get("host").orElseThrow();
 * }</pre>
 */
@Service
public class ConfigBean {

    private static final Logger log = LoggerFactory.getLogger(ConfigBean.class);

    private final AppConfigRepository repository;

    private final ConcurrentHashMap<String, AppConfigDto> cache = new ConcurrentHashMap<>();

    public ConfigBean(AppConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Loads all active rows from {@code app_config} and prints them in a
     * formatted table. Runs once after the full application context is ready
     * (DB seeded, all beans initialized).
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

    // -------------------------------------------------------------------------

    private void printTable() {
        if (cache.isEmpty()) {
            log.warn("ConfigBean: app_config table has no active rows.");
            return;
        }

        // Column widths
        int keyW  = cache.values().stream().mapToInt(r -> r.configKey().length()).max().orElse(10);
        int typeW = cache.values().stream().mapToInt(r -> r.configType().length()).max().orElse(6);
        int valW  = cache.values().stream().mapToInt(r -> r.configValue().length()).max().orElse(10);

        keyW  = Math.max(keyW,  10);
        typeW = Math.max(typeW,  4);
        valW  = Math.max(valW,  5);

        String fmt      = "| %-" + keyW + "s | %-" + typeW + "s | %-" + valW + "s |";
        String divider  = "+" + "-".repeat(keyW + 2) + "+" + "-".repeat(typeW + 2) + "+" + "-".repeat(valW + 2) + "+";

        log.info("ConfigBean: {} active config entries loaded", cache.size());
        log.info(divider);
        log.info(String.format(fmt, "config_key", "type", "value"));
        log.info(divider);
        cache.values().stream()
                .sorted((a, b) -> {
                    int cmp = a.configType().compareTo(b.configType());
                    return cmp != 0 ? cmp : a.configKey().compareTo(b.configKey());
                })
                .forEach(r -> log.info(String.format(fmt, r.configKey(), r.configType(), r.configValue())));
        log.info(divider);
    }

    // -------------------------------------------------------------------------

    /** Returns the full config row for the given key. */
    public Optional<AppConfigDto> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    /** Convenience: returns just the config_value string for the given key. */
    public Optional<String> getValue(String key) {
        return get(key).map(AppConfigDto::configValue);
    }

    /** Returns an unmodifiable view of the full cache. */
    public Map<String, AppConfigDto> getAll() {
        return Collections.unmodifiableMap(cache);
    }

    public boolean containsKey(String key) {
        return cache.containsKey(key);
    }

    public int size() {
        return cache.size();
    }

    /** Clears and reloads from DB — for admin-triggered hot-reload. */
    public int refresh() {
        loadOnReady();
        return cache.size();
    }
}
