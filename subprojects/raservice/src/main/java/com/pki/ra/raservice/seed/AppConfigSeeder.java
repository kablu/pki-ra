package com.pki.ra.raservice.seed;

import com.pki.ra.common.config.AppConfigRepository;
import com.pki.ra.common.model.AppConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds sample {@code app_config} rows for local H2 development.
 *
 * <p>Runs as {@link org.springframework.boot.ApplicationRunner} — before
 * {@code ApplicationReadyEvent} — so data is present when
 * {@link com.pki.ra.common.config.ConfigBean#loadOnReady()} fires.
 *
 * <p>Idempotent: skipped automatically if the table already contains data
 * (handled by {@link AbstractH2Seeder#run(org.springframework.boot.ApplicationArguments)}).
 *
 * @see AbstractH2Seeder
 */
@Component
@Profile("h2")
@Order(1)
public class AppConfigSeeder extends AbstractH2Seeder {

    private final AppConfigRepository repository;

    public AppConfigSeeder(AppConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    protected long count() {
        return repository.count();
    }

    @Override
    protected void seed() {
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
