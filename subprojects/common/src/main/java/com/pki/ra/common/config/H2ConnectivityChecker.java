package com.pki.ra.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Bean named <b>"DB"</b> — verifies H2 connectivity immediately after the
 * Spring context finishes wiring all beans.
 *
 * <h3>When does it run?</h3>
 * <p>Implements {@link InitializingBean}.  Spring calls {@code afterPropertiesSet()}
 * once every dependency of this bean ({@link DataSource}) is fully initialised —
 * before the application starts accepting requests.  If the check fails the
 * application aborts with a clear error message (fail-fast).
 *
 * <h3>What it checks</h3>
 * <ol>
 *   <li>Acquires a physical JDBC {@link Connection} from the Hikari pool.</li>
 *   <li>Calls {@code Connection.isValid(timeout)} — standard JDBC liveness check.</li>
 *   <li>Executes {@code SELECT H2VERSION()} — H2-specific query confirming the
 *       engine is H2 and returning its version string.</li>
 *   <li>Reads {@link DatabaseMetaData} and logs: product name, version, JDBC URL,
 *       driver name, and max connections.</li>
 * </ol>
 *
 * <h3>Profile</h3>
 * <p>Active only under profile {@code h2} — mirrors {@link DataSourceConfig}.
 */
@Slf4j
@Component("DB")
@Profile("h2")
public class H2ConnectivityChecker implements InitializingBean {

    private static final String BANNER =
            "\n╔══════════════════════════════════════════════════╗" +
            "\n║         H2 DATABASE CONNECTIVITY CHECK           ║" +
            "\n╚══════════════════════════════════════════════════╝";

    private static final int VALIDITY_TIMEOUT_SECONDS = 3;

    private final DataSource dataSource;

    public H2ConnectivityChecker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Called by Spring after all bean properties are set.
     * Fails fast with {@link IllegalStateException} if H2 is unreachable.
     */
    @Override
    public void afterPropertiesSet() {
        log.info(BANNER);
        checkConnectivity();
    }

    // ── Core check ─────────────────────────────────────────────────────────────

    private void checkConnectivity() {
        long start = System.currentTimeMillis();

        try (Connection connection = dataSource.getConnection()) {

            // 1. JDBC liveness check
            boolean valid = connection.isValid(VALIDITY_TIMEOUT_SECONDS);
            if (!valid) {
                throw new IllegalStateException(
                        "H2 connection acquired but isValid() returned false — " +
                        "connection may be stale or H2 engine is unresponsive.");
            }

            // 2. H2-specific version query
            String h2Version = queryH2Version(connection);

            // 3. Database metadata
            DatabaseMetaData meta = connection.getMetaData();

            long elapsed = System.currentTimeMillis() - start;

            log.info("╔══════════════════════════════════════════════════╗");
            log.info("║  [DB] H2 Connectivity — OK ({} ms)              ", elapsed);
            log.info("╠══════════════════════════════════════════════════╣");
            log.info("║  Product   : {} {}", meta.getDatabaseProductName(),
                                             meta.getDatabaseProductVersion());
            log.info("║  H2 Version: {}", h2Version);
            log.info("║  JDBC URL  : {}", meta.getURL());
            log.info("║  Driver    : {} v{}.{}",
                    meta.getDriverName(),
                    meta.getDriverMajorVersion(),
                    meta.getDriverMinorVersion());
            log.info("║  User      : {}", meta.getUserName());
            log.info("║  Max Conns : {}", meta.getMaxConnections());
            log.info("╚══════════════════════════════════════════════════╝");

        } catch (SQLException ex) {
            log.error("╔══════════════════════════════════════════════════╗");
            log.error("║  [DB] H2 Connectivity — FAILED                   ║");
            log.error("╠══════════════════════════════════════════════════╣");
            log.error("║  Error    : {}", ex.getMessage());
            log.error("║  SQLState : {}", ex.getSQLState());
            log.error("║  Code     : {}", ex.getErrorCode());
            log.error("╚══════════════════════════════════════════════════╝");
            throw new IllegalStateException(
                    "[DB] Application startup aborted — H2 connectivity check failed: "
                    + ex.getMessage(), ex);
        }
    }

    // ── H2VERSION() query ──────────────────────────────────────────────────────

    private String queryH2Version(Connection connection) throws SQLException {
        try (var stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT H2VERSION()")) {
            return rs.next() ? rs.getString(1) : "unknown";
        }
    }
}
