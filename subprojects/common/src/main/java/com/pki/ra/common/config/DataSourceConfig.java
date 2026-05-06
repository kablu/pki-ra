package com.pki.ra.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

import javax.sql.DataSource;

/**
 * H2 in-memory {@link DataSource} configuration — active only under profile {@code h2}.
 *
 * <h3>Activation</h3>
 * <pre>
 *   --spring.profiles.active=h2          (command line)
 *   spring.profiles.active=h2            (application.yml)
 *   SPRING_PROFILES_ACTIVE=h2            (environment variable)
 * </pre>
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li>Properties are loaded from {@code classpath:db.properties} via {@code @PropertySource}.</li>
 *   <li>All values are bound into {@link DatabaseProperties} via {@code @ConfigurationProperties}.</li>
 *   <li>A Hikari connection pool wraps the H2 in-memory engine.</li>
 *   <li>{@code @Primary} ensures this DataSource takes precedence if multiple are present.</li>
 * </ul>
 *
 * <h3>H2 Console</h3>
 * <p>When active, the H2 web console is available at
 * {@code http://localhost:<port>/h2-console} (JDBC URL: {@code jdbc:h2:mem:pki_ra}).
 * Remote access is disabled by default ({@code webAllowOthers=false}).
 */
@Slf4j
@Configuration
@Profile("h2")
@PropertySource("classpath:db.properties")
@EnableConfigurationProperties(DatabaseProperties.class)
public class DataSourceConfig {

    private final DatabaseProperties props;

    public DataSourceConfig(DatabaseProperties props) {
        this.props = props;
    }

    /**
     * Creates and returns a Hikari-pooled H2 {@link DataSource}.
     *
     * <p>All connection parameters are read from {@link DatabaseProperties},
     * which is bound from {@code db.properties}.  No values are hard-coded here.
     *
     * @return configured H2 DataSource
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        DatabaseProperties.Datasource ds    = props.getDatasource();
        DatabaseProperties.Hikari     hikari = ds.getHikari();

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(ds.getUrl());
        cfg.setUsername(ds.getUsername());
        cfg.setPassword(ds.getPassword());
        cfg.setDriverClassName(ds.getDriverClassName());
        cfg.setPoolName(hikari.getPoolName());
        cfg.setMaximumPoolSize(hikari.getMaximumPoolSize());
        cfg.setMinimumIdle(hikari.getMinimumIdle());
        cfg.setConnectionTimeout(hikari.getConnectionTimeout());
        cfg.setIdleTimeout(hikari.getIdleTimeout());
        cfg.setMaxLifetime(hikari.getMaxLifetime());
        cfg.setAutoCommit(hikari.isAutoCommit());

        // H2-specific health check — runs on every connection acquisition
        cfg.setConnectionTestQuery("SELECT 1");

        log.info("H2 DataSource initialised — url={}, pool={}, maxPoolSize={}",
                ds.getUrl(), hikari.getPoolName(), hikari.getMaximumPoolSize());

        return new HikariDataSource(cfg);
    }
}
