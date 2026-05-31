package com.pki.ra.raservice.config;

import com.pki.ra.common.config.ConfigBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Demonstrates how ra-service reads specific config keys from ConfigBean.
 *
 * Runs at @Order(20) — after ConfigBean (@Order(10)) has populated the cache.
 * Any service/component in ra-service can autowire ConfigBean and call
 * configBean.getValue("key") or configBean.get("key") the same way.
 */
@Component
public class RaServiceConfigReader {

    private static final Logger log = LoggerFactory.getLogger(RaServiceConfigReader.class);

    private final ConfigBean configBean;

    public RaServiceConfigReader(ConfigBean configBean) {
        this.configBean = configBean;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(20)
    public void readConfigs() {
        log.info("--- RA-Service reading config values from ConfigBean ---");

        String[] keys = { "host", "port", "baseDn", "useSsl", "connectionTimeoutMs", "readTimeoutMs" };

        for (String key : keys) {
            configBean.get(key).ifPresentOrElse(
                dto -> log.info(String.format("  key=%-20s  type=%-6s  value=%s",
                                              dto.configKey(), dto.configType(), dto.configValue())),
                ()  -> log.warn("  key={}  — NOT FOUND in config cache", key)
            );
        }

        log.info("--- End of config read ---");
    }
}
