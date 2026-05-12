package com.pki.ra.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pki.ra.common.config.dto.ConfigDto;
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
import java.util.stream.Collectors;

/**
 * In-memory cache for all database-backed application configuration.
 *
 * <p>On {@link ApplicationReadyEvent}, fetches all active rows from
 * {@code app_config} via JPA.  Rows are grouped by {@code config_type}.
 * Each group is assembled into a flat {@code Map<String, String>} of
 * {@code config_key → config_value}, then converted into the appropriate
 * {@link ConfigDto} subtype using {@link ObjectMapper#convertValue}.
 *
 * <p>Example — three rows in DB with {@code config_type = "LDAP"}:
 * <pre>
 *   host  | LDAP | ldap.pki.internal
 *   port  | LDAP | 636
 *   useSsl| LDAP | true
 * </pre>
 * are assembled into {@code LdapConfigDto} and stored in cache under key {@code "LDAP"}.
 *
 * <p>After startup, <strong>no DB call is made on the request path</strong>.
 * All reads are pure in-memory lookups.
 *
 * <p>{@code @Order(10)} ensures this listener fires before consumer listeners
 * (e.g. {@code RaConfigLogger} at {@code @Order(20)}).
 *
 * <p>Usage:
 * <pre>{@code
 * LdapConfigDto ldap = configBean.get("LDAP", LdapConfigDto.class)
 *         .orElseThrow(() -> new IllegalStateException("LDAP config missing"));
 * }</pre>
 */
@Service
public class ConfigBean {

    private static final Logger log = LoggerFactory.getLogger(ConfigBean.class);

    private final AppConfigRepository repository;
    private final ConfigDtoRegistry   registry;
    private final ObjectMapper        objectMapper;

    private final ConcurrentHashMap<String, ConfigDto> cache = new ConcurrentHashMap<>();

    public ConfigBean(AppConfigRepository repository,
                      ConfigDtoRegistry registry,
                      ObjectMapper objectMapper) {
        this.repository  = repository;
        this.registry    = registry;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Startup loading
    // -------------------------------------------------------------------------

    /**
     * Groups all active rows by {@code config_type}, builds a flat property map
     * per type, then converts each map to the registered {@link ConfigDto} subtype.
     * Cache key = {@code config_type} (e.g. {@code "LDAP"}).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void loadOnReady() {
        log.info("ConfigBean: loading configuration from app_config via JPA...");
        cache.clear();
        var loaded  = 0;
        var skipped = 0;

        // Group all active rows by config_type
        var grouped = repository.findAllActive()
                .stream()
                .collect(Collectors.groupingBy(
                        row -> row.getConfigType(),
                        Collectors.toMap(
                                row -> row.getConfigKey(),
                                row -> row.getConfigValue()
                        )
                ));

        for (var entry : grouped.entrySet()) {
            var type  = entry.getKey();
            var props = entry.getValue();
            try {
                var dtoClass = registry.resolve(type);
                // Convert flat Map<String,String> → DTO (Jackson handles type coercion)
                var dto = objectMapper.convertValue(props, dtoClass);
                cache.put(type, dto);
                log.debug("ConfigBean: cached type='{}' fields={}", type, props.keySet());
                loaded++;
            } catch (Exception ex) {
                log.warn("ConfigBean: skipping type='{}' — {}", type, ex.getMessage());
                skipped++;
            }
        }

        log.info("ConfigBean: ready — {} type(s) loaded, {} skipped.", loaded, skipped);
    }

    // -------------------------------------------------------------------------
    // Typed accessors  (cache key = config_type, e.g. "LDAP")
    // -------------------------------------------------------------------------

    /**
     * Returns the cached {@link ConfigDto} for the given {@code configType}.
     *
     * @param configType the {@code config_type} discriminator, e.g. {@code "LDAP"}
     */
    public Optional<ConfigDto> get(String configType) {
        return Optional.ofNullable(cache.get(configType));
    }

    /**
     * Returns the cached config cast to {@code type}, or empty if absent or type mismatch.
     *
     * @param configType the {@code config_type} discriminator, e.g. {@code "LDAP"}
     * @param type       expected DTO class, e.g. {@code LdapConfigDto.class}
     */
    public <T extends ConfigDto> Optional<T> get(String configType, Class<T> type) {
        return Optional.ofNullable(cache.get(configType))
                .filter(type::isInstance)
                .map(type::cast);
    }

    // -------------------------------------------------------------------------
    // Cache management
    // -------------------------------------------------------------------------

    /** Returns an unmodifiable view of the full cache — for logging and diagnostics. */
    public Map<String, ConfigDto> getAll() {
        return Collections.unmodifiableMap(cache);
    }

    /** Returns {@code true} if the cache contains an entry for {@code configType}. */
    public boolean containsKey(String configType) {
        return cache.containsKey(configType);
    }

    /** Returns the number of config types currently in the cache. */
    public int size() {
        return cache.size();
    }

    /**
     * Clears and reloads the cache from the database.
     * For admin-triggered hot-reload without service restart.
     */
    public int refresh() {
        log.info("ConfigBean: manual cache refresh triggered.");
        cache.clear();
        loadOnReady();
        return cache.size();
    }
}
