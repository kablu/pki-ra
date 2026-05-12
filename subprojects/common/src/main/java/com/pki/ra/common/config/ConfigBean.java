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
 * <p>Loaded once on startup ({@link ApplicationReadyEvent}).
 * Key = {@code config_key}, value = full row as {@link AppConfigDto}.
 * No DB calls on the request path after startup.
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

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void loadOnReady() {
        log.info("ConfigBean: loading app_config rows...");
        cache.clear();

        repository.findAllActive().forEach(row ->
            cache.put(row.getConfigKey(), new AppConfigDto(
                    row.getConfigKey(),
                    row.getConfigType(),
                    row.getConfigValue(),
                    row.isActive()
            ))
        );

        log.info("ConfigBean: loaded {} entries.", cache.size());
    }

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
