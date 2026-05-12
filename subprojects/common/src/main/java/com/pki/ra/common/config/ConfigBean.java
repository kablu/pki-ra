package com.pki.ra.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pki.ra.common.config.dto.ConfigDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for all database-backed application configuration.
 *
 * <p>Uses the existing {@link javax.sql.DataSource} bean (declared in
 * {@code DataSourceConfig}) via Spring Boot's auto-configured
 * {@link JdbcTemplate} — no JPA repository required.
 *
 * <p>On {@link ApplicationReadyEvent}, a single SQL query fetches every active
 * row from {@code app_config}.  Each row's {@code config_value} JSON is
 * deserialised into the appropriate {@link ConfigDto} subtype via
 * {@link ConfigDtoRegistry} and stored in a {@link ConcurrentHashMap}
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

    private static final String FETCH_ACTIVE_SQL =
            "SELECT config_key, config_type, config_value FROM app_config WHERE is_active = TRUE";

    private final JdbcTemplate      jdbcTemplate;
    private final ConfigDtoRegistry registry;
    private final ObjectMapper      objectMapper;

    private final ConcurrentHashMap<String, ConfigDto> cache = new ConcurrentHashMap<>();

    public ConfigBean(JdbcTemplate jdbcTemplate,
                      ConfigDtoRegistry registry,
                      ObjectMapper objectMapper) {
        this.jdbcTemplate  = jdbcTemplate;
        this.registry      = registry;
        this.objectMapper  = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Startup loading  — uses existing DataSource bean via JdbcTemplate
    // -------------------------------------------------------------------------

    /**
     * Loads all active configuration entries from {@code app_config} into the
     * in-memory cache.  Fired once the application context is fully ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void loadOnReady() {
        log.info("ConfigBean: loading configuration from app_config via DataSource...");
        cache.clear();
        var loaded  = 0;
        var skipped = 0;

        var rows = jdbcTemplate.queryForList(FETCH_ACTIVE_SQL);
        for (var row : rows) {
            var key   = (String) row.get("config_key");
            var type  = (String) row.get("config_type");
            var value = (String) row.get("config_value");
            try {
                var dtoClass = registry.resolve(type);
                var dto      = objectMapper.readValue(value, dtoClass);
                cache.put(key, dto);
                log.debug("ConfigBean: cached key='{}' type='{}'", key, type);
                loaded++;
            } catch (Exception ex) {
                log.warn("ConfigBean: skipping key='{}' type='{}' — {}", key, type, ex.getMessage());
                skipped++;
            }
        }

        log.info("ConfigBean: ready — {} loaded, {} skipped.", loaded, skipped);
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
