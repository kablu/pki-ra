package com.pki.ra.raservice.seed;

import com.pki.ra.common.config.AppConfigRepository;
import com.pki.ra.common.model.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Inserts sample {@code app_config} rows for local H2 development.
 *
 * <p>Active only under the {@code h2} Spring profile.  Uses
 * {@link AppConfigRepository} (JPA) for the INSERT — consistent with how
 * {@code ConfigBean} reads data via the same repository.
 *
 * <p>Because {@link ApplicationRunner} executes <em>before</em>
 * {@code ApplicationReadyEvent} is published, the rows inserted here are
 * guaranteed to be present when {@code ConfigBean.loadOnReady()} fires.
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

        repository.save(AppConfig.builder()
                .configKey("isSecLdap")
                .configType("LDAP")
                .configValue("""
                        {"host":"ldap.pki.internal","port":636,"baseDn":"DC=pki,DC=internal",\
                        "bindDn":"CN=svc-pki-bind,OU=ServiceAccounts,DC=pki,DC=internal",\
                        "bindPassword":"change-me","useSsl":true,\
                        "connectionTimeoutMs":5000,"readTimeoutMs":10000}""")
                .description("Active Directory LDAP connectivity settings")
                .isActive(true)
                .build());

        log.info("AppConfigSeeder: {} row(s) in app_config after seed.", repository.count());
    }
}
