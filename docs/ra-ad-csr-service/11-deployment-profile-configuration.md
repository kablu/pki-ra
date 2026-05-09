# RA AD CSR Service — Deployment and Profile Configuration

**Document type:** Deployment Guide
**Module:** `pki-ra/subprojects/ra-ad-csr-service`

---

## 1. Overview

The service supports three runtime profiles. A profile controls which database engine is used and which environment-specific properties are activated. No code changes are required when switching between profiles — only the active profile and environment-specific property values change.

| Profile | Database | Purpose |
|---|---|---|
| `h2` | H2 in-memory | Local development and automated testing |
| `mariadb` | MariaDB 10.11+ | Staging and integration environments |
| `prod` | MariaDB 10.11+ | Production deployment |

The active profile is set via the `spring.profiles.active` property, which is provided as a JVM argument at startup or via an environment variable.

---

## 2. Profile Activation

**Local development:**
The `h2` profile is activated by default when running from the IDE. The `application-h2.properties` file is picked up automatically by Spring Boot and applies H2-specific settings on top of the common properties.

**Staging and Production:**
The active profile is passed as a JVM argument when launching the service JAR. For example, to activate the production profile, the argument `--spring.profiles.active=prod` is appended to the java launch command. Alternatively, the `SPRING_PROFILES_ACTIVE` environment variable can be set in the operating system or container environment before the JVM starts, and Spring Boot will detect it automatically without any explicit argument.

---

## 3. Properties File Structure

The `src/main/resources` directory contains the following files:

- **`application.properties`** — Properties that apply to every profile. Contains server port, JPA settings, Flyway configuration, actuator exposure, and other environment-independent settings.
- **`application-h2.properties`** — Overrides applied only when the `h2` profile is active. Enables SQL logging, the H2 browser console, and debug-level application logging.
- **`application-mariadb.properties`** — Overrides applied only when the `mariadb` profile is active. Switches the JPA dialect and suppresses SQL logging.
- **`application-prod.properties`** — Overrides applied only when the `prod` profile is active. Disables error detail exposure, tightens log levels, and restricts actuator endpoints.
- **`db.properties`** — DataSource and Hikari connection pool configuration. Loaded by the `DataSourceConfig` bean in `:common` via a `@PropertySource` annotation. This file contains the JDBC URL, credentials, and pool tuning parameters.

Spring Boot loads `application.properties` first, then merges the active profile-specific file on top. Any property defined in the profile file overrides the value from the common file.

---

## 4. `application.properties` — Common Properties

These properties apply to all profiles.

| Property | Value | Description |
|---|---|---|
| `spring.application.name` | `ra-ad-csr-service` | Service identifier in logs and Spring Boot admin |
| `server.port` | `8082` | HTTP listening port |
| `server.servlet.context-path` | `/` | Root context path |
| `server.tomcat.max-threads` | `50` | Maximum concurrent request threads |
| `server.tomcat.min-spare-threads` | `5` | Minimum idle threads |
| `server.tomcat.connection-timeout` | `20000` | Connection timeout in milliseconds |
| `spring.jpa.open-in-view` | `false` | Disabled to prevent lazy-load pitfalls |
| `spring.jpa.show-sql` | `false` | SQL logging via Hibernate (enabled per-profile in dev) |
| `spring.flyway.enabled` | `true` | Always run Flyway migrations on startup |
| `spring.flyway.locations` | `classpath:db/migration` | Location of versioned migration scripts |
| `spring.flyway.baseline-on-migrate` | `false` | Never baseline — always apply all migrations |
| `spring.main.allow-bean-definition-overriding` | `false` | Never override `:common` beans |
| `management.endpoints.web.exposure.include` | `health,info` | Only expose safe actuator endpoints |
| `management.endpoint.health.show-details` | `never` | Hide internal details from health endpoint in all environments |

---

## 5. `application-h2.properties` — H2 Profile

| Property | Value | Description |
|---|---|---|
| `spring.jpa.show-sql` | `true` | Print all SQL to console |
| `spring.jpa.hibernate.ddl-auto` | `none` | Flyway manages schema — never let Hibernate auto-create |
| `spring.jpa.database-platform` | `org.hibernate.dialect.H2Dialect` | H2-specific SQL dialect |
| `spring.h2.console.enabled` | `true` | Enable H2 browser console at /h2-console |
| `spring.h2.console.path` | `/h2-console` | H2 console URL path |
| `spring.h2.console.settings.web-allow-others` | `false` | Accessible from localhost only |
| `logging.level.com.pki.ra` | `DEBUG` | Full debug logging for application code |
| `logging.level.org.hibernate.SQL` | `DEBUG` | Log all SQL statements |
| `logging.level.org.springframework.security` | `DEBUG` | Log security filter decisions |

---

## 6. `application-mariadb.properties` — MariaDB Staging Profile

| Property | Value | Description |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `none` | Flyway manages schema |
| `spring.jpa.database-platform` | `org.hibernate.dialect.MariaDBDialect` | MariaDB SQL dialect |
| `spring.h2.console.enabled` | `false` | No H2 console in non-H2 profiles |
| `logging.level.com.pki.ra` | `INFO` | Reduced logging for staging |
| `logging.level.org.hibernate.SQL` | `OFF` | No SQL logging in staging |

---

## 7. `application-prod.properties` — Production Profile

| Property | Value | Description |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `none` | Flyway manages schema |
| `spring.jpa.database-platform` | `org.hibernate.dialect.MariaDBDialect` | MariaDB SQL dialect |
| `spring.h2.console.enabled` | `false` | Never enabled in production |
| `server.error.include-message` | `never` | Never expose exception messages in error responses |
| `server.error.include-stacktrace` | `never` | Never expose stack traces |
| `logging.level.com.pki.ra` | `INFO` | Business events only |
| `logging.level.root` | `WARN` | Suppress framework debug noise |
| `management.endpoints.web.exposure.include` | `health` | Only health endpoint in production |

---

## 8. `db.properties` — DataSource Configuration

This file is loaded by `DataSourceConfig` from `:common` via `@PropertySource("classpath:db.properties")`. All database connection parameters are defined here.

**H2 configuration (development):**

| Property | Value |
|---|---|
| `db.datasource.url` | `jdbc:h2:mem:ra_ad_csr;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE` |
| `db.datasource.username` | `sa` |
| `db.datasource.password` | *(empty)* |
| `db.datasource.driver-class-name` | `org.h2.Driver` |
| `db.datasource.hikari.pool-name` | `RA-AD-CSR-Pool` |
| `db.datasource.hikari.maximum-pool-size` | `5` |
| `db.datasource.hikari.minimum-idle` | `1` |
| `db.datasource.hikari.connection-timeout` | `20000` |
| `db.datasource.hikari.idle-timeout` | `300000` |
| `db.datasource.hikari.max-lifetime` | `900000` |
| `db.h2.console.enabled` | `true` |
| `db.h2.console.path` | `/h2-console` |
| `db.h2.console.settings.web-allow-others` | `false` |

**MariaDB configuration (staging and production):**

| Property | Value |
|---|---|
| `db.datasource.url` | `jdbc:mariadb://${DB_HOST}:3306/${DB_NAME}?useSSL=true&requireSSL=true` |
| `db.datasource.username` | `${DB_USERNAME}` |
| `db.datasource.password` | `${DB_PASSWORD}` |
| `db.datasource.driver-class-name` | `org.mariadb.jdbc.Driver` |
| `db.datasource.hikari.pool-name` | `RA-AD-CSR-Pool` |
| `db.datasource.hikari.maximum-pool-size` | `20` |
| `db.datasource.hikari.minimum-idle` | `5` |
| `db.datasource.hikari.connection-timeout` | `30000` |
| `db.datasource.hikari.idle-timeout` | `600000` |
| `db.datasource.hikari.max-lifetime` | `1800000` |

---

## 9. Environment Variables

Sensitive values are never stored in property files. They are provided as environment variables at runtime and referenced via `${VARIABLE_NAME}` in properties.

| Environment Variable | Used In | Description |
|---|---|---|
| `DB_HOST` | `db.properties` | MariaDB hostname or IP |
| `DB_NAME` | `db.properties` | Database name |
| `DB_USERNAME` | `db.properties` | Database user |
| `DB_PASSWORD` | `db.properties` | Database password |
| `SPRING_PROFILES_ACTIVE` | JVM / OS | Active Spring profile |
| `SERVER_PORT` | `application.properties` | Override default port (optional) |

---

## 10. Startup Sequence

The following events occur in order when the service starts:

| Step | What happens |
|---|---|
| 1 | JVM starts, Spring Boot bootstrap begins |
| 2 | `application.properties` loaded, then profile-specific override |
| 3 | `DataSourceConfig` creates `HikariDataSource` (`:common` bean) |
| 4 | Flyway runs pending migrations (V1, V2, V3 on first startup) |
| 5 | Spring Data JPA initialises entity manager and repositories |
| 6 | `DatabaseConfig` enables JPA auditing and transaction management |
| 7 | `SecurityFilterChain` bean is created — CORS, CSRF, headers configured |
| 8 | `ApplicationReadyEvent` fires |
| 9 | `ErrorCodeCacheService.loadOnStartup()` reads all active error codes from DB into cache |
| 10 | `RaConfigCacheService.loadOnStartup()` reads all active config entries from DB into cache |
| 11 | Tomcat begins accepting requests |

If step 9 or 10 fails (e.g. DB is unreachable at startup), the application will fail to start. This is intentional — the service must not operate without its error code and configuration caches populated.

---

## 11. Health Check

Spring Boot Actuator exposes a `/actuator/health` endpoint. In production, the response body shows only the overall `UP` or `DOWN` status without any details. In development, component details (DB connectivity, disk space) are visible.

The health endpoint is used by load balancers and monitoring systems to determine whether the instance is ready to receive traffic. A `DOWN` response causes the load balancer to remove the instance from rotation.

---

## 12. Deployment Checklist

| Item | Check |
|---|---|
| `SPRING_PROFILES_ACTIVE` set to `prod` | ✓ |
| `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` set | ✓ |
| MariaDB reachable from host | ✓ |
| Flyway migrations applied cleanly (check startup log) | ✓ |
| Error code cache loaded — log shows count | ✓ |
| Config cache loaded — log shows count | ✓ |
| `/actuator/health` returns `{"status":"UP"}` | ✓ |
| H2 console disabled (`spring.h2.console.enabled=false`) | ✓ |
| Error messages suppressed (`server.error.include-message=never`) | ✓ |
| Stack traces suppressed (`server.error.include-stacktrace=never`) | ✓ |
| Log files writing to correct location | ✓ |
| Proxy IP whitelist configured in `ra_config` table | ✓ |
