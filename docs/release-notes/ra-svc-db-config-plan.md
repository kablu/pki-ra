# Branch: `ra-svc-db-config` — Complete Plan of Changes

**Branch:** `ra-svc-db-config`
**Base:** `main`
**Date:** 2026-05-12
**Author:** PKI-RA Development Team

---

## Overview

This branch introduces three areas of change:

| Area | Type | Description |
|---|---|---|
| 1 | Feature | DB-backed configuration system with typed DTO cache |
| 2 | Docs | `ra-ad-csr-service` 13-section detail component design |
| 3 | Fix | `raservice` H2 console, connectivity checker, infrastructure |

---

## Area 1 — DB-Backed Configuration System

### Goal

Replace `.properties`-based configuration with an `app_config` database table.
All config is loaded into an in-memory `ConcurrentHashMap` on `ApplicationReadyEvent`
and served from cache at request time — zero DB calls per request.

---

### `:common` Module — New Files

#### `subprojects/common/src/main/java/com/pki/ra/common/model/AppConfig.java`

- JPA Entity mapped to the `app_config` table
- Extends `BaseAuditEntity` — inherits `created_at`, `created_by`, `updated_at`, `updated_by`
- Fields:

| Field | Column | Type | Notes |
|---|---|---|---|
| `id` | `id` | `BIGINT` | Auto-increment PK |
| `configKey` | `config_key` | `VARCHAR(100)` | Unique, e.g. `isSecLdap` |
| `configType` | `config_type` | `VARCHAR(50)` | Discriminator, e.g. `LDAP` |
| `configValue` | `config_value` | `TEXT` | JSON-serialised DTO payload |
| `description` | `description` | `VARCHAR(500)` | Human-readable label |
| `isActive` | `is_active` | `BOOLEAN` | `false` excludes from cache |

- Table is created automatically by Hibernate via `ddl-auto` — no migration script needed

---

#### `subprojects/common/src/main/java/com/pki/ra/common/config/dto/ConfigDto.java`

- `sealed interface` — type contract for all config value DTOs
- All permitted DTO types must implement `configType()` returning their discriminator string
- Adding a new config type requires adding it to the `permits` clause

---

#### `subprojects/common/src/main/java/com/pki/ra/common/config/dto/LdapConfigDto.java`

- Java `record` implementing `ConfigDto`
- `config_type` discriminator = `LDAP`
- Config key = `isSecLdap`

| Field | Type | Example |
|---|---|---|
| `host` | String | `ldap.pki.internal` |
| `port` | int | `636` |
| `baseDn` | String | `DC=pki,DC=internal` |
| `bindDn` | String | `CN=svc-pki-bind,OU=ServiceAccounts,DC=pki,DC=internal` |
| `bindPassword` | String | `change-me` |
| `useSsl` | boolean | `true` |
| `connectionTimeoutMs` | int | `5000` |
| `readTimeoutMs` | int | `10000` |

---

#### `subprojects/common/src/main/java/com/pki/ra/common/config/AppConfigRepository.java`

- `JpaRepository<AppConfig, Long>`
- Discovered by `@EnableJpaRepositories(basePackages = "com.pki.ra")` in `DatabaseConfig`

| Method | Query | Purpose |
|---|---|---|
| `findAllActive()` | `SELECT c FROM AppConfig c WHERE c.isActive = true` | Startup cache load |
| `findActiveByKey(key)` | `SELECT c FROM AppConfig c WHERE c.configKey = :configKey AND c.isActive = true` | Fallback single-key lookup |

---

#### `subprojects/common/src/main/java/com/pki/ra/common/config/ConfigDtoRegistry.java`

- `@Component` — maps `config_type` string to DTO `Class` for Jackson deserialisation
- Static `Map.of("LDAP", LdapConfigDto.class)`
- To add a new type: add entry to the map

---

#### `subprojects/common/src/main/java/com/pki/ra/common/config/ConfigBean.java`

- `@Service` — central in-memory config cache
- Holds `ConcurrentHashMap<String, ConfigDto>` keyed by `config_key`
- `@EventListener(ApplicationReadyEvent.class) @Order(10)` — fires before any consumer

| Method | Description |
|---|---|
| `loadOnReady()` | Calls `repository.findAllActive()`, deserialises each JSON value, populates cache |
| `get(key)` | Returns `Optional<ConfigDto>` |
| `get(key, Class<T>)` | Returns `Optional<T>` — type-safe typed access |
| `getAll()` | Returns unmodifiable view of full cache — for logging and diagnostics |
| `refresh()` | Clears and reloads cache from DB — for admin hot-reload |

---

### `:common` Module — Modified Files

#### `subprojects/common/src/main/java/com/pki/ra/common/config/DatabaseConfig.java`

- Added `@Bean @ConditionalOnMissingBean ObjectMapper` with `JavaTimeModule`
- Required by `ConfigBean` for JSON deserialisation of `config_value`
- `@ConditionalOnMissingBean` ensures it does not conflict if Spring Boot auto-configures one

---

### `raservice` Module — New Files

#### `subprojects/raservice/src/main/java/com/pki/ra/raservice/config/RaConfigLogger.java`

- `@Component` — logs entire config cache on startup
- `@EventListener(ApplicationReadyEvent.class) @Order(20)` — fires **after** `ConfigBean @Order(10)`
- Output format:

```
=== RA-Service Configuration Dump — 1 entry ===
  [CONFIG] key='isSecLdap'  type='LDAP'  value=LdapConfigDto[host=ldap.pki.internal, ...]
=== End Configuration Dump ===
```

---

#### `subprojects/raservice/src/main/java/com/pki/ra/raservice/seed/AppConfigSeeder.java`

- `@Component @Profile("h2")` — active only in local H2 development
- Implements `ApplicationRunner` — runs **before** `ApplicationReadyEvent`
- Inserts `isSecLdap / LDAP` sample row using `AppConfigRepository.save()`
- Skips insert if table already has data

---

### Startup Sequence

```
Step 1 — Hibernate creates app_config table (ddl-auto: create-drop)

Step 2 — AppConfigSeeder.run()        [ApplicationRunner — before ApplicationReadyEvent]
         → repository.save(isSecLdap / LDAP JSON row)

Step 3 — ConfigBean.loadOnReady()     [@Order(10) on ApplicationReadyEvent]
         → repository.findAllActive() JPQL
         → objectMapper.readValue(json, LdapConfigDto.class)
         → cache.put("isSecLdap", dto)

Step 4 — RaConfigLogger.logAllConfigs() [@Order(20) on ApplicationReadyEvent]
         → configBean.getAll()
         → log each key / type / value
```

---

### How to Use ConfigBean in Any Service or Controller

Inject via constructor (Spring Boot 4 standard — no `@Autowired` on fields):

```java
private final ConfigBean configBean;

public MyService(ConfigBean configBean) {
    this.configBean = configBean;
}

// Typed access
LdapConfigDto ldap = configBean.get("isSecLdap", LdapConfigDto.class)
        .orElseThrow(() -> new IllegalStateException("LDAP config missing"));
```

---

### How to Add a New Config Type

1. Create a new `record` implementing `ConfigDto`:
   ```java
   public record MailConfigDto(String host, int port, String username) implements ConfigDto {
       public static final String TYPE = "MAIL";
       public String configType() { return TYPE; }
   }
   ```
2. Add it to `ConfigDto` sealed interface `permits` clause
3. Register in `ConfigDtoRegistry.REGISTRY`: `"MAIL", MailConfigDto.class`
4. Insert a row in `app_config` with `config_type = 'MAIL'` and JSON value

---

## Area 2 — `ra-ad-csr-service` Documentation

13 markdown design documents and a generated Word file added under `docs/ra-ad-csr-service/`.

| File | Content |
|---|---|
| `01-analysis-and-plan.md` | Problem statement, actors, scope, architecture, delivery plan |
| `02-component-design.md` | Module structure, component descriptions, request/response flow |
| `03-api-specification.md` | Controller, DTOs, filters, validators, service and repository specs |
| `04-common-bean-reuse.md` | Which `:common` beans `ra-ad-csr-service` reuses and how |
| `05-error-code-strategy.md` | `error_code_catalog` table, 25 error codes, `ErrorCodeCacheService` |
| `06-ra-config-table.md` | `ra_config` table, 27 config keys across 5 groups, cache service |
| `07-database-design.md` | All 5 tables, ER relationships, Flyway migration order, indexing |
| `08-csr-validation-pipeline.md` | 9-stage Bouncy Castle CSR validation pipeline |
| `09-security-architecture.md` | 11 security layers, threat model, CSRF/CORS/IP whitelist |
| `10-logging-observability.md` | MDC context, log levels per package, audit vs application log |
| `11-deployment-profile-configuration.md` | H2/MariaDB profiles, environment variables, deployment checklist |
| `12-testing-strategy.md` | Test pyramid, coverage targets, CI gates |
| `13-ad-authentication-payload.md` | AD header payload structure, `AdIdentity`, request/response format |
| `detail-component-design.docx` | Auto-generated Word document from all 13 sections (138 KB) |
| `docgen/generate.js` | Node.js generator script using `docx@9.x` |

---

## Area 3 — `raservice` Infrastructure Fixes

| File | Change |
|---|---|
| `raservice/config/H2ConsoleSecurityConfig.java` | Permits H2 web console under `h2` profile in Spring Security 7 |
| `raservice/checker/RaSVCH2Checker.java` | Verifies `DataSource` bean from `:common` is injectable at startup |
| `raservice/listener/RaServiceReadyListener.java` | Logs `ApplicationReadyEvent` — confirms service is fully up |
| `raservice/repository/RequestLogRepository.java` | JPA repository for `request_log` table |
| `raservice/audit/AuditLog.java` | Local audit log entity wired to `AuditLogService` from `:common` |
| `application-h2.yml` | H2 profile config — clears Flyway exclusion, `ddl-auto: create-drop` |
| `RaServiceApplication.java` | Added `@EntityScan(basePackages = "com.pki.ra.common.model")` |

---

## Complete File Change Summary

| Action | Module | File |
|---|---|---|
| **NEW** | `:common` | `model/AppConfig.java` |
| **NEW** | `:common` | `config/dto/ConfigDto.java` |
| **NEW** | `:common` | `config/dto/LdapConfigDto.java` |
| **NEW** | `:common` | `config/AppConfigRepository.java` |
| **NEW** | `:common` | `config/ConfigDtoRegistry.java` |
| **NEW** | `:common` | `config/ConfigBean.java` |
| **MODIFIED** | `:common` | `config/DatabaseConfig.java` — added `ObjectMapper` bean |
| **NEW** | `raservice` | `config/RaConfigLogger.java` |
| **NEW** | `raservice` | `seed/AppConfigSeeder.java` |
| **NEW** | `raservice` | `config/H2ConsoleSecurityConfig.java` |
| **NEW** | `raservice` | `checker/RaSVCH2Checker.java` |
| **NEW** | `raservice` | `repository/RequestLogRepository.java` |
| **MODIFIED** | `raservice` | `RaServiceApplication.java` — added `@EntityScan` |
| **NEW** | `docs` | 13 markdown files + `detail-component-design.docx` |
