package com.pki.ra.raservice.config;

import com.pki.ra.common.config.ConfigBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Logs the complete in-memory configuration cache on startup.
 *
 * <p>Runs on {@link ApplicationReadyEvent} at {@code @Order(20)}, which guarantees
 * it fires <em>after</em> {@code ConfigBean} (at {@code @Order(10)}) has already
 * populated the cache.  This produces a full audit trail of every active config key
 * and its resolved DTO in the application log.
 *
 * <p>Output format:
 * <pre>
 * === RA-Service Configuration Dump — 1 entries ===
 *   [CONFIG] key='isSecLdap'  type='LDAP'  value=LdapConfigDto[host=ldap.pki.internal, ...]
 * === End Configuration Dump ===
 * </pre>
 */
@Component
public class RaConfigLogger {

    private static final Logger log = LoggerFactory.getLogger(RaConfigLogger.class);

    private final ConfigBean configBean;

    public RaConfigLogger(ConfigBean configBean) {
        this.configBean = configBean;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(20)
    public void logAllConfigs() {
        var all = configBean.getAll();

        log.info("=== RA-Service Configuration Dump — {} {} ===",
                all.size(), all.size() == 1 ? "entry" : "entries");

        if (all.isEmpty()) {
            log.warn("  [CONFIG] No active configuration entries found in DB.");
        } else {
            all.forEach((key, dto) ->
                    log.info("  [CONFIG] key='{}'  type='{}'  value={}", key, dto.configType(), dto));
        }

        log.info("=== End Configuration Dump ===");
    }
}
