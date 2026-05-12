package com.pki.ra.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pki.ra.common.config.dto.ConfigDto;
import com.pki.ra.common.model.AppConfig;
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
 * In-memory cache for all database-backed application configuration.
 *
 * <p>On {@link ApplicationReadyEvent} (fired once Spring context is fully ready),
 * this service reads every active row from the {@code app_config} table, deserialises
 * the {@code config_value} JSON into the appropriate {@link ConfigDto} subtype via
 * {@link ConfigDtoRegistry}, and stores the result in a {@link ConcurrentHashMap}
 * keyed by {@code config_key}.
 *
 * <p>After startup, <strong>no database call is made on the request path</strong>.
 * All config reads are pure in-memory lookups.
 *
 * <p>{@code @Order(10)} ensures this listener fires before any consumer listeners
 * (e.g. {@code RaConfigLogger} at {@code @Order(20)}) that depend on the populated cache.
 *
 * <p>Usage example:
 * <pre>{@code
 * LdapConfigDto ldap = configBean.get("isSecLdap", LdapConfigDto.class)
 *         .orElseThrow(() -> new IllegalStateException("LDAP config missing"));
 * }</pre>
 */
@Service
public class ConfigBean {

    private static final Logger log = LoggerFactory.getLogger(ConfigBean.class);

    private final AppConfigRepository  repository;
    private final ConfigDtoRegistry    registry;
    private final ObjectMapper         objectMapper;

    private final ConcurrentHashMap<String, ConfigDto> cache = new ConcurrentHashMap<>();

    public ConfigBean(AppConfigRepository repository,
                      ConfigDtoRegistry registry,
                      ObjectMapper objectMapper) {
        this.repository   = repository;
        this.registry     = registry;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Startup loading
    // -------------------------------------------------------------------------

    /**
     * Loads all active configuration entries from the database into the cache.
     * Fired once the application context is fully initialised.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void loadOnReady() {
        log.info("ConfigBean: loading application configuration from DB...");
        var loaded  = 0;
        var skipped = 0;

        for (AppConfig row : repository.findAllByIsActiveTrue()) {
            try {
                var dtoClass = registry.resolve(row.getConfigType());
                var dto      = objectMapper.readValue(row.getConfigValue(), dtoClass);
                cache.put(row.getConfigKey(), dto);
                log.debug("ConfigBean: cached key='{}' type='{}'", row.getConfigKey(), row.getConfigType());
                loaded++;
            } catch (Exception ex) {
                log.warn("ConfigBean: skipping key='{}' type='{}' — {}",
                        row.getConfigKey(), row.getConfigType(), ex.getMessage());
                skipped++;
            }
        }

        log.info("ConfigBean: configuration ready — {} loaded, {} skipped.", loaded, skipped);
    }

    // -------------------------------------------------------------------------
    // Typed accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the cached {@link ConfigDto} for {@code key}, or empty if absent.
     *
     * @param key the {@code config_key} value, e.g. {@code "isSecLdap"}
     */
    public Optional<ConfigDto> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    /**
     * Returns the cached config cast to {@code type}, or empty if absent or wrong type.
     *
     * @param key  the {@code config_key} value
     * @param type the expected DTO class, e.g. {@code LdapConfigDto.class}
     */
    public <T extends ConfigDto> Optional<T> get(String key, Class<T> type) {
        return Optional.ofNullable(cache.get(key))
                .filter(type::isInstance)
                .map(type::cast);
    }

    // -------------------------------------------------------------------------
    // Cache management
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable view of the entire cache — useful for diagnostics and logging.
     */
    public Map<String, ConfigDto> getAll() {
        return Collections.unmodifiableMap(cache);
    }

    /** Returns {@code true} if the cache contains an entry for {@code key}. */
    public boolean containsKey(String key) {
        return cache.containsKey(key);
    }

    /** Returns the number of entries currently in the cache. */
    public int size() {
        return cache.size();
    }

    /**
     * Clears and reloads the cache from the database.
     * Intended for admin-triggered hot-reload without service restart.
     *
     * @return number of active entries loaded after refresh
     */
    public int refresh() {
        log.info("ConfigBean: manual cache refresh triggered.");
        cache.clear();
        loadOnReady();
        return cache.size();
    }
}
