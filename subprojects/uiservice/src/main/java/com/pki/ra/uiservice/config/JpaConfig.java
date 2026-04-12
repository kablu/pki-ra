package com.pki.ra.uiservice.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Complete JPA + HikariCP configuration for :uiservice.
 *
 * <p><b>Why manual configuration instead of Spring Boot auto-config?</b>
 * <ul>
 *   <li>Explicit control over HikariCP pool tuning</li>
 *   <li>Custom entity scan packages</li>
 *   <li>Production-ready Hibernate properties</li>
 *   <li>Visible to the whole team — no hidden magic</li>
 * </ul>
 *
 * <p><b>Beans created here:</b>
 * <ol>
 *   <li>{@link DataSource}                         — HikariCP connection pool</li>
 *   <li>{@link LocalContainerEntityManagerFactoryBean} — JPA engine (Hibernate)</li>
 *   <li>{@link PlatformTransactionManager}          — JPA transactions</li>
 * </ol>
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Slf4j
@Configuration
@EnableTransactionManagement          // @Transactional annotations enable karta hai
@EnableJpaAuditing                    // @CreatedDate, @LastModifiedDate enable karta hai
@EnableJpaRepositories(
    basePackages = "com.pki.ra.uiservice.repository"  // Repository scan location
)
public class JpaConfig {

    // -------------------------------------------------------------------------
    // DataSource properties — application.yml se inject honge
    // -------------------------------------------------------------------------

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    // -------------------------------------------------------------------------
    // HikariCP pool properties
    // -------------------------------------------------------------------------

    @Value("${spring.datasource.hikari.maximum-pool-size:20}")
    private int maximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minimumIdle;

    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    @Value("${spring.datasource.hikari.keepalive-time:60000}")
    private long keepaliveTime;

    @Value("${spring.datasource.hikari.pool-name:PkiRaHikariPool}")
    private String poolName;

    // =========================================================================
    // BEAN 1 — DataSource (HikariCP Connection Pool)
    // =========================================================================

    /**
     * HikariCP DataSource — DB connection pool.
     *
     * <p><b>HikariCP kyun?</b>
     * <ul>
     *   <li>Spring Boot ka default pool — fastest Java connection pool</li>
     *   <li>Minimal overhead, zero-wait connection acquisition</li>
     *   <li>Production-proven at Netflix, Twitter scale</li>
     * </ul>
     *
     * <p><b>Key pool settings:</b>
     * <ul>
     *   <li>{@code maximumPoolSize}  — max concurrent DB connections</li>
     *   <li>{@code minimumIdle}      — always-ready connections (warm pool)</li>
     *   <li>{@code connectionTimeout}— wait time before throwing exception</li>
     *   <li>{@code maxLifetime}      — connection forced recycle (< DB timeout)</li>
     *   <li>{@code keepaliveTime}    — ping idle connections (prevent firewall drop)</li>
     * </ul>
     */
    @Bean
    @Profile("!test")
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        // -- Connection --
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setDriverClassName(driverClassName);

        // -- Pool sizing --
        config.setMaximumPoolSize(maximumPoolSize);     // max DB connections
        config.setMinimumIdle(minimumIdle);             // always warm connections

        // -- Timeouts --
        config.setConnectionTimeout(connectionTimeout); // 30s — wait for connection
        config.setIdleTimeout(idleTimeout);             // 10m — idle connection retire
        config.setMaxLifetime(maxLifetime);             // 30m — force recycle
        config.setKeepaliveTime(keepaliveTime);         // 1m  — ping idle connections

        // -- Pool name (visible in JMX, logs) --
        config.setPoolName(poolName);

        // -- Connection validation --
        config.setConnectionTestQuery("SELECT 1");      // PostgreSQL health check

        // -- PostgreSQL specific optimizations --
        config.addDataSourceProperty("cachePrepStmts",          "true");
        config.addDataSourceProperty("prepStmtCacheSize",        "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit",    "2048");
        config.addDataSourceProperty("useServerPrepStmts",       "true");
        config.addDataSourceProperty("reWriteBatchedInserts",    "true");  // batch insert speed

        log.info("HikariCP pool initialized: pool={}, maxSize={}, minIdle={}",
                 poolName, maximumPoolSize, minimumIdle);

        return new HikariDataSource(config);
    }

    // =========================================================================
    // BEAN 2 — EntityManagerFactory (JPA Engine — Hibernate)
    // =========================================================================

    /**
     * JPA EntityManagerFactory — Hibernate configure karta hai.
     *
     * <p><b>Kya karta hai?</b>
     * <ul>
     *   <li>Entity classes scan karta hai ({@code com.pki.ra})</li>
     *   <li>Hibernate ko DataSource se connect karta hai</li>
     *   <li>DDL validation, SQL logging, dialect configure karta hai</li>
     * </ul>
     */
    @Bean
    @Profile("!test")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean emf =
                new LocalContainerEntityManagerFactoryBean();

        // -- DataSource wire --
        emf.setDataSource(dataSource);

        // -- Entity scan — in packages yeh @Entity dhundega --
        emf.setPackagesToScan(
            "com.pki.ra.common.model",      // :common ke base entities
            "com.pki.ra.uiservice.model"    // :uiservice ke entities
        );

        // -- JPA Provider: Hibernate --
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false);        // DDL Flyway handle karega
        vendorAdapter.setShowSql(false);            // production mein false
        emf.setJpaVendorAdapter(vendorAdapter);

        // -- Hibernate Properties --
        emf.setJpaProperties(hibernateProperties());

        return emf;
    }

    /**
     * Hibernate tuning properties.
     *
     * <p>Har property ka purpose:
     * <ul>
     *   <li>{@code ddl-auto=validate}         — Schema Flyway se manage, Hibernate sirf validate kare</li>
     *   <li>{@code dialect}                    — PostgreSQL specific SQL generate karne ke liye</li>
     *   <li>{@code format_sql}                 — Debug mein readable SQL</li>
     *   <li>{@code jdbc.time_zone=UTC}         — Timestamp always UTC mein store ho</li>
     *   <li>{@code jdbc.batch_size}            — Bulk insert/update performance</li>
     *   <li>{@code order_inserts/updates=true} — Batch grouping for efficiency</li>
     *   <li>{@code open-in-view=false}         — LazyLoading trap avoid karo</li>
     * </ul>
     */
    private Properties hibernateProperties() {
        Properties props = new Properties();

        // Schema management — NEVER use create/update in production
        props.put("hibernate.hbm2ddl.auto",                      "validate");

        // PostgreSQL dialect — correct SQL syntax generate hogi
        props.put("hibernate.dialect",
                  "org.hibernate.dialect.PostgreSQLDialect");

        // SQL logging (dev mein true, prod mein false)
        props.put("hibernate.show_sql",                          "false");
        props.put("hibernate.format_sql",                        "true");
        props.put("hibernate.use_sql_comments",                  "false");

        // Timezone — DB mein always UTC
        props.put("hibernate.jdbc.time_zone",                    "UTC");

        // Batch processing — bulk insert/update fast hoga
        props.put("hibernate.jdbc.batch_size",                   "50");
        props.put("hibernate.order_inserts",                     "true");
        props.put("hibernate.order_updates",                     "true");
        props.put("hibernate.jdbc.batch_versioned_data",         "true");

        // Statistics — production mein false (overhead)
        props.put("hibernate.generate_statistics",               "false");

        // Second-level cache — abhi disabled (future mein Ehcache/Redis)
        props.put("hibernate.cache.use_second_level_cache",      "false");
        props.put("hibernate.cache.use_query_cache",             "false");

        return props;
    }

    // =========================================================================
    // BEAN 3 — PlatformTransactionManager (JPA Transactions)
    // =========================================================================

    /**
     * JPA Transaction Manager.
     *
     * <p><b>JpaTransactionManager kyun?</b>
     * <ul>
     *   <li>JPA EntityManager ka lifecycle manage karta hai</li>
     *   <li>{@code @Transactional} ke saath kaam karta hai</li>
     *   <li>Commit/Rollback JPA context ke saath sync mein hota hai</li>
     *   <li>JdbcTransactionManager se different — JPA entity state flush karta hai</li>
     * </ul>
     */
    @Bean
    @Profile("!test")
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory) {

        JpaTransactionManager txManager =
                new JpaTransactionManager(entityManagerFactory);

        log.info("JpaTransactionManager initialized");
        return txManager;
    }
}
