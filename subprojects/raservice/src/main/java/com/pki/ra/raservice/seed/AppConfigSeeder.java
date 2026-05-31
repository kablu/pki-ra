package com.pki.ra.raservice.seed;

import com.pki.ra.common.config.AppConfigRepository;
import com.pki.ra.common.model.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Inserts sample {@code app_config} rows for local H2 development.
 *
 * <p>Each row holds a single plain-string field value — no JSON.
 * All rows with the same {@code config_type} are assembled into one DTO
 * by {@code ConfigBean} on {@code ApplicationReadyEvent}.
 *
 * <p>Runs as {@link ApplicationRunner} — before {@code ApplicationReadyEvent} —
 * so data is present when {@code ConfigBean.loadOnReady()} fires.
 */
@Component
@Profile("h2")
public class AppConfigSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppConfigSeeder.class);

    private final AppConfigRepository repository;

    public AppConfigSeeder(AppConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            log.info("AppConfigSeeder: app_config already has data — skipping seed.");
            return;
        }

        log.info("AppConfigSeeder: seeding sample app_config rows...");

        repository.saveAll(List.of(

            // ── LDAP config — one row per field ──────────────────────────────
            row("host",                "LDAP", "ldap.pki.internal",                                     "LDAP server hostname"),
            row("port",                "LDAP", "636",                                                   "LDAP port (636 = LDAPS)"),
            row("baseDn",              "LDAP", "DC=pki,DC=internal",                                    "Base DN for user search"),
            row("bindDn",              "LDAP", "CN=svc-pki-bind,OU=ServiceAccounts,DC=pki,DC=internal", "Service account DN"),
            row("bindPassword",        "LDAP", "change-me",                                             "Service account password"),
            row("useSsl",              "LDAP", "true",                                                  "Enable LDAPS (SSL)"),
            row("connectionTimeoutMs", "LDAP", "5000",                                                  "Connection timeout in ms"),
            row("readTimeoutMs",       "LDAP", "10000",                                                 "Read timeout in ms")

        ));

        log.info("AppConfigSeeder: {} row(s) inserted.", repository.count());
    }

    private AppConfig row(String key, String type, String value, String description) {
        return AppConfig.builder()
                .configKey(key)
                .configType(type)
                .configValue(value)
                .description(description)
                .isActive(true)
                .build();
    }
}
