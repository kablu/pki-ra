package com.pki.ra.raservice.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Inserts sample {@code app_config} rows for local H2 development.
 *
 * <p>Active only under the {@code h2} Spring profile.  Uses the existing
 * {@link javax.sql.DataSource} bean (from {@code DataSourceConfig}) via
 * {@link JdbcTemplate} — consistent with how {@code ConfigBean} reads data.
 *
 * <p>Because {@link ApplicationRunner} executes <em>before</em>
 * {@code ApplicationReadyEvent} is published, the rows inserted here are
 * guaranteed to be present when {@code ConfigBean.loadOnReady()} fires.
 */
@Component
@Profile("h2")
public class AppConfigSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppConfigSeeder.class);

    private final JdbcTemplate jdbcTemplate;

    public AppConfigSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_config", Integer.class);

        if (count != null && count > 0) {
            log.info("AppConfigSeeder: app_config already has data — skipping seed.");
            return;
        }

        log.info("AppConfigSeeder: seeding sample app_config rows...");

        jdbcTemplate.update("""
                INSERT INTO app_config
                    (config_key, config_type, config_value, description, is_active,
                     created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, ?, NOW(), 'system', NOW(), 'system')
                """,
                "isSecLdap",
                "LDAP",
                """
                {"host":"ldap.pki.internal","port":636,"baseDn":"DC=pki,DC=internal",\
                "bindDn":"CN=svc-pki-bind,OU=ServiceAccounts,DC=pki,DC=internal",\
                "bindPassword":"change-me","useSsl":true,\
                "connectionTimeoutMs":5000,"readTimeoutMs":10000}""",
                "Active Directory LDAP connectivity settings",
                true
        );

        var inserted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_config", Integer.class);
        log.info("AppConfigSeeder: {} row(s) in app_config after seed.", inserted);
    }
}
