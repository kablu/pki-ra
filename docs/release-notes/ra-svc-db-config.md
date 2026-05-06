# Release Notes — ra-svc-db-config

| Field | Detail |
|---|---|
| **Branch** | `ra-svc-db-config` |
| **Base Branch** | `feature/employee-jpa-crud` |
| **Date** | 2026-05-01 |
| **Author** | PKI-RA Project Team |
| **Type** | Feature / Configuration |
| **Scope** | `raservice` module |

---

## Summary

This change activates the H2 in-memory database connectivity bean (`"DB"`) inside the
`raservice` module. Previously, `raservice` excluded all DataSource and JPA
auto-configurations, making it a pure stateless service with no database wiring.
Under the new `h2` Spring profile, `raservice` now initialises the shared H2 `DataSource`
from `:common`, runs the startup connectivity check, and wires the full JPA auditing
infrastructure — with zero changes to the `:common` module.

---

## Problem Statement

When `raservice` was started with `--spring.profiles.active=h2`, the `"DB"` bean
(`H2ConnectivityChecker`) defined in `:common` never executed. Root causes:

| # | Cause | Effect |
|---|---|---|
| 1 | `application.yml` globally excluded `DataSourceAutoConfiguration` | H2 `DataSource` bean could not be created |
| 2 | `application.yml` globally excluded `HibernateJpaAutoConfiguration` | `DatabaseConfig#@EnableJpaAuditing` failed — JPA infrastructure absent |
| 3 | `spring-boot-data-jpa` dependency missing from `raservice` | No JPA infrastructure on classpath at all |
| 4 | `bootRun` profile set to `dev` | `@Profile("h2")` beans (`DataSourceConfig`, `H2ConnectivityChecker`) never activated |

---

## Changes Applied

### 1. `subprojects/raservice/build.gradle`

**Added dependency — `spring-boot-data-jpa`**

`DatabaseConfig` (declared in `:common`, always active) carries `@EnableJpaAuditing`.
This annotation requires JPA infrastructure (specifically `LocalContainerEntityManagerFactoryBean`)
on the classpath. Without `spring-boot-data-jpa`, `raservice` crashed on context load
with a missing-bean error for `EntityManagerFactory`.

```groovy
// ADDED
implementation libs.spring.boot.data.jpa
```

**Changed `bootRun` profile: `dev` → `h2`**

`DataSourceConfig` and `H2ConnectivityChecker` are both guarded by `@Profile("h2")`.
The previous profile (`dev`) left those beans dormant.

```groovy
// BEFORE
'-Dspring.profiles.active=dev'

// AFTER
'-Dspring.profiles.active=h2'
```

---

### 2. `subprojects/raservice/src/main/resources/application.yml`

**Removed `DataSourceAutoConfiguration` and `HibernateJpaAutoConfiguration` from global exclude list**

These two exclusions prevented the shared H2 `DataSource` (created by `DataSourceConfig`
in `:common`) and the JPA infrastructure (required by `DatabaseConfig`) from
initialising. `FlywayAutoConfiguration` is retained because `raservice` carries
no Flyway migration scripts and must not attempt schema migration in any profile.

```yaml
# BEFORE
autoconfigure:
  exclude:
    - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
    - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
    - org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration

# AFTER
autoconfigure:
  exclude:
    - org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
```

---

### 3. `subprojects/raservice/src/main/resources/application-h2.yml` *(new file)*

**New profile-specific configuration for H2 environment**

Provides `raservice`-specific overrides when the `h2` profile is active:

| Property | Value | Reason |
|---|---|---|
| `spring.autoconfigure.exclude` | `[]` | Clears the Flyway exclusion from `application.yml` — common's `application-h2.yml` disables Flyway via `spring.flyway.enabled=false` |
| `spring.jpa.hibernate.ddl-auto` | `create-drop` | `raservice` has no JPA entities; Hibernate needs a valid DDL mode to initialise without migration scripts |
| `spring.jpa.show-sql` | `true` | Verbose SQL in dev/h2 environment for debugging |
| `spring.h2.console.enabled` | `true` | H2 web console at `http://localhost:8083/ra-api/h2-console` |
| `logging.level.com.zaxxer.hikari` | `DEBUG` | Pool lifecycle logs visible during H2 startup |

---

## Bean Activation Flow (h2 profile)

```
raservice bootRun --spring.profiles.active=h2
│
├── application.yml         → Flyway excluded; DataSource + JPA allowed
├── application-h2.yml (common classpath) → ddl-auto=create-drop, flyway.enabled=false
├── application-h2.yml (raservice)        → autoconfigure.exclude=[], Hikari DEBUG
│
├── DataSourceConfig  [@Profile("h2"), common]
│     └── @Bean @Primary DataSource → HikariDataSource (jdbc:h2:mem:pki_ra)
│
├── DatabaseConfig  [always active, common]
│     ├── @EnableJpaAuditing(auditorAwareRef="auditorAwareImpl")
│     └── @EnableJpaRepositories(basePackages="com.pki.ra")
│
├── AuditorAwareImpl  [@Component("auditorAwareImpl"), common]
│     └── Resolves current AD username from Spring Security context
│
└── H2ConnectivityChecker  [@Component("DB"), @Profile("h2"), common]
      └── InitializingBean.afterPropertiesSet()
            ├── connection.isValid(3)       → JDBC liveness check
            ├── SELECT H2VERSION()          → confirms H2 engine + version
            └── DatabaseMetaData            → logs URL, driver, user, max connections
```

---

## Expected Startup Output

On successful `bootRun` with `h2` profile, the following log sequence appears:

```
INFO  c.p.r.raservice.RaServiceApplication    - The following 1 profile is active: "h2"
INFO  c.p.r.c.config.DataSourceConfig         - H2 DataSource initialised — url=jdbc:h2:mem:pki_ra, pool=PKI-RA-H2-Pool, maxPoolSize=5

╔══════════════════════════════════════════════════╗
║         H2 DATABASE CONNECTIVITY CHECK           ║
╚══════════════════════════════════════════════════╝

╔══════════════════════════════════════════════════╗
║  [DB] H2 Connectivity — OK (xx ms)
╠══════════════════════════════════════════════════╣
║  Product   : H2 2.4.240
║  H2 Version: 2.4.240
║  JDBC URL  : jdbc:h2:mem:pki_ra
║  Driver    : H2 JDBC Driver v2.4
║  User      : SA
║  Max Conns : 0
╚══════════════════════════════════════════════════╝

INFO  c.p.r.r.l.RaServiceReadyListener        - PKI-RA :: raservice  >>  PHASE 3 : Application Ready
```

---

## H2 Console Access (h2 profile only)

| Field | Value |
|---|---|
| URL | `http://localhost:8083/ra-api/h2-console` |
| JDBC URL | `jdbc:h2:mem:pki_ra` |
| Username | `sa` |
| Password | *(empty)* |

---

## Files Changed

| File | Change Type | Description |
|---|---|---|
| `subprojects/raservice/build.gradle` | Modified | Added `spring-boot-data-jpa`; changed `bootRun` profile `dev` → `h2` |
| `subprojects/raservice/src/main/resources/application.yml` | Modified | Removed `DataSourceAutoConfiguration` + `HibernateJpaAutoConfiguration` from exclude list |
| `subprojects/raservice/src/main/resources/application-h2.yml` | **New** | H2 profile overrides — clears exclusions, sets ddl-auto, enables H2 console |
| `docs/release-notes/ra-svc-db-config.md` | **New** | This release notes document |

**No changes to `:common` module** — `DataSourceConfig`, `H2ConnectivityChecker`,
`DatabaseConfig`, `db.properties`, and `application-h2.yml` were already in place.

---

## Backward Compatibility

| Profile | Behaviour | Notes |
|---|---|---|
| `h2` | H2 in-memory DB, full connectivity check | **New — this change** |
| `dev` | No DataSource (Flyway still excluded) | Unchanged — `dev` profile has no datasource config |
| `prod` / default | MariaDB via external datasource config | Unaffected — no changes to production path |

---

## Related Branches / Issues

| Item | Reference |
|---|---|
| Base work (H2 in common) | `feature/employee-jpa-crud` — commit `a02a049` |
| DB bean creation | `feature/employee-jpa-crud` — commit `081ab0d` |
| scanBasePackages fix | `feature/employee-jpa-crud` — commit `0893670` |
| Duplicate bean fix | `feature/employee-jpa-crud` — commit `69b0ee1` |
