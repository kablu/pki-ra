# RA AD CSR Service — RA Configuration Table

**Document type:** Runtime Configuration Management
**Module:** `pki-ra/subprojects/ra-ad-csr-service`

---

## 1. Overview

The `ra_config` table stores all runtime configuration parameters for the RA platform. Instead of hardcoding values in `application.properties` or environment variables, operational parameters (IP whitelists, timeouts, limits, feature flags, CSR policy values) are persisted in the database and loaded into an in-memory cache on application startup via `ApplicationReadyEvent`.

**Benefits:**
- Change configuration without redeployment
- Audit trail via `BaseAuditEntity` — who changed what and when
- Admin API to reload cache at runtime
- Centralised config visible to all RA service modules

**Key design decisions:**
- All configs are `String` values — callers cast to the required type using typed accessor methods
- Every key belongs to a `config_group` for organisational clarity
- `is_active = false` disables a key without deleting it
- Cache is always the source of truth at request time — no DB calls per request

---

## 2. `ra_config` Table Schema

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGINT | NO | Auto-increment primary key |
| `config_key` | VARCHAR(100) | NO | Unique dotted key, e.g. `csr.max.size.bytes` |
| `config_value` | VARCHAR(1000) | NO | String value — caller casts to required type |
| `config_group` | VARCHAR(50) | NO | Logical group: CSR, SECURITY, AD, AUDIT, SYSTEM |
| `description` | VARCHAR(500) | YES | Human-readable explanation of the config |
| `is_active` | BOOLEAN | NO | `false` disables the key; defaults to `true` |
| `created_at` | TIMESTAMP | NO | Auto-populated by `BaseAuditEntity` |
| `created_by` | VARCHAR(100) | NO | AD username of creator |
| `updated_at` | TIMESTAMP | NO | Auto-updated by `BaseAuditEntity` |
| `updated_by` | VARCHAR(100) | NO | AD username of last modifier |

A `UNIQUE` constraint is enforced on `config_key`. The table has no foreign keys — it is a standalone key-value store.

---

## 3. Configuration Groups and Keys

### 3.1 Group: `CSR`

| Config Key | Default Value | Description |
|---|---|---|
| `csr.max.size.bytes` | `8192` | Maximum PEM-encoded CSR size in bytes |
| `csr.allowed.signature.algorithms` | `SHA256withRSA,SHA384withRSA,SHA512withRSA,SHA256withECDSA` | Comma-separated allowed signature algorithms |
| `csr.allowed.key.sizes` | `2048,3072,4096` | Allowed RSA key sizes (bits) |
| `csr.allowed.ec.curves` | `P-256,P-384,P-521` | Allowed EC named curves |
| `csr.cn.max.length` | `64` | Maximum characters in Common Name |
| `csr.validity.max.days` | `1095` | Maximum certificate validity period in days |
| `csr.duplicate.window.hours` | `24` | Hours within which duplicate CSR hash is rejected |

### 3.2 Group: `SECURITY`

| Config Key | Default Value | Description |
|---|---|---|
| `security.proxy.whitelist.ips` | `127.0.0.1,::1` | Comma-separated trusted proxy IP addresses |
| `security.rate.limit.requests` | `100` | Max requests per window per proxy IP |
| `security.rate.limit.window.seconds` | `60` | Rate-limit sliding window in seconds |
| `security.header.max.length` | `512` | Maximum length of any incoming header value |
| `security.cors.allowed.origins` | `https://ra.internal` | Comma-separated CORS allowed origins |
| `security.csrf.cookie.secure` | `true` | Whether CSRF cookie has Secure flag |

### 3.3 Group: `AD`

| Config Key | Default Value | Description |
|---|---|---|
| `ad.header.username` | `X-AD-Username` | HTTP header name for AD sAMAccountName |
| `ad.header.display.name` | `X-AD-DisplayName` | HTTP header name for AD display name |
| `ad.header.email` | `X-AD-Email` | HTTP header name for AD email |
| `ad.header.groups` | `X-AD-Groups` | HTTP header name for AD group memberships |
| `ad.username.max.length` | `20` | Maximum length of AD sAMAccountName |
| `ad.required.groups` | `RA-Users` | Comma-separated AD groups required to submit CSR |

### 3.4 Group: `AUDIT`

| Config Key | Default Value | Description |
|---|---|---|
| `audit.log.retain.days` | `365` | Days to retain audit log entries |
| `audit.request.log.enabled` | `true` | Whether to persist RequestLog for every request |
| `audit.sensitive.fields` | `password,token,secret` | Fields to mask in audit descriptions |

### 3.5 Group: `SYSTEM`

| Config Key | Default Value | Description |
|---|---|---|
| `system.maintenance.mode` | `false` | When `true`, all submissions return 503 |
| `system.config.cache.ttl.minutes` | `60` | Advisory TTL — admin refresh is authoritative |
| `system.max.concurrent.submissions` | `50` | Max concurrent CSR submissions in flight |
| `system.submission.timeout.seconds` | `30` | Timeout for a single CSR submission |

---

## 4. `RaConfig` — JPA Entity

**Package:** `com.pki.ra.adcsr.config`
**Table:** `ra_config`
**Extends:** `BaseAuditEntity` (from `:common`)
**Annotations:** `@Entity`, `@Table(name = "ra_config")`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`

The entity maps the `ra_config` table and inherits `created_at`, `created_by`, `updated_at`, and `updated_by` from `BaseAuditEntity`. Its own declared fields are:

| Field | Column | Type | Constraints | Description |
|---|---|---|---|---|
| `id` | `id` | Long | PK, AUTO_INCREMENT | Surrogate key |
| `configKey` | `config_key` | String (100) | NOT NULL, UNIQUE | Dotted configuration key |
| `configValue` | `config_value` | String (1000) | NOT NULL | String value for the key |
| `configGroup` | `config_group` | String (50) | NOT NULL | Logical group name |
| `description` | `description` | String (500) | Nullable | Human-readable explanation |
| `isActive` | `is_active` | boolean | NOT NULL, defaults to true | Whether the entry is loaded into cache |

---

## 5. `RaConfigRepository`

**Package:** `com.pki.ra.adcsr.config`
**Extends:** `JpaRepository<RaConfig, Long>`

| Method | Return Type | Description |
|---|---|---|
| `findAllByIsActiveTrue()` | List of RaConfig | Returns all active config entries — called at startup and on refresh |
| `findByConfigKeyAndIsActiveTrue(configKey)` | Optional of RaConfig | Lookup a single active key — used as fallback if cache misses |
| `findAllByConfigGroupAndIsActiveTrue(configGroup)` | List of RaConfig | Returns all active keys in a group — admin and diagnostic use |

At request time, the repository is never called. All reads go through the `RaConfigCacheService` in-memory cache.

---

## 6. `RaConfigCacheService`

**Package:** `com.pki.ra.adcsr.config`
**Annotation:** `@Service`
**Dependencies:** `RaConfigRepository`

**Internal state:**
- A `ConcurrentHashMap<String, RaConfig>` keyed by `config_key` string, holding the full `RaConfig` entity as the value.

**Startup loading:**
The service is annotated with `@EventListener(ApplicationReadyEvent.class)` on its `loadOnStartup()` method. When the event fires, the method calls `refresh()`, logs the number of active entries loaded, and the cache is ready.

**Typed accessor methods:**

| Method | Return Type | Behaviour |
|---|---|---|
| `get(key)` | String | Returns the config value. Throws `ConfigKeyNotFoundException` if the key is absent from cache. |
| `get(key, defaultValue)` | String | Returns the config value, or `defaultValue` if the key is absent. Never throws. |
| `getInt(key, defaultValue)` | int | Parses the string value as an integer. Returns `defaultValue` if absent or if parsing fails. |
| `getBoolean(key, defaultValue)` | boolean | Parses the string value as a boolean. Returns `defaultValue` if absent. |
| `getList(key)` | List of String | Splits the value by comma, trims each part, and filters empties. Returns an empty list if the key is absent or the value is blank. |
| `getGroup(configGroup)` | Map of String to String | Streams all cache entries, filters by `config_group`, and returns a map of key to value for all entries in that group. |

**Cache refresh:**
The `refresh()` method is annotated with `@Transactional(readOnly = true)`. It clears the map, calls `repository.findAllByIsActiveTrue()`, and repopulates the map entry by entry. It returns the count of loaded entries. This method is also called by the admin refresh endpoint.

---

## 7. Config Key Constants

The `RaConfigKeys` class is a final utility class with a private constructor, containing only public static String constants. Constants are organised by group using inline comments. Using these constants instead of raw string literals throughout the codebase prevents typos and makes refactoring safe.

**CSR group constants:**

| Constant | String Value |
|---|---|
| `CSR_MAX_SIZE_BYTES` | `csr.max.size.bytes` |
| `CSR_ALLOWED_SIG_ALGORITHMS` | `csr.allowed.signature.algorithms` |
| `CSR_ALLOWED_KEY_SIZES` | `csr.allowed.key.sizes` |
| `CSR_ALLOWED_EC_CURVES` | `csr.allowed.ec.curves` |
| `CSR_CN_MAX_LENGTH` | `csr.cn.max.length` |
| `CSR_VALIDITY_MAX_DAYS` | `csr.validity.max.days` |
| `CSR_DUPLICATE_WINDOW_HOURS` | `csr.duplicate.window.hours` |

**SECURITY group constants:**

| Constant | String Value |
|---|---|
| `SECURITY_PROXY_WHITELIST_IPS` | `security.proxy.whitelist.ips` |
| `SECURITY_RATE_LIMIT_REQUESTS` | `security.rate.limit.requests` |
| `SECURITY_RATE_LIMIT_WINDOW_SECS` | `security.rate.limit.window.seconds` |
| `SECURITY_HEADER_MAX_LENGTH` | `security.header.max.length` |
| `SECURITY_CORS_ALLOWED_ORIGINS` | `security.cors.allowed.origins` |
| `SECURITY_CSRF_COOKIE_SECURE` | `security.csrf.cookie.secure` |

**AD group constants:**

| Constant | String Value |
|---|---|
| `AD_HEADER_USERNAME` | `ad.header.username` |
| `AD_HEADER_DISPLAY_NAME` | `ad.header.display.name` |
| `AD_HEADER_EMAIL` | `ad.header.email` |
| `AD_HEADER_GROUPS` | `ad.header.groups` |
| `AD_USERNAME_MAX_LENGTH` | `ad.username.max.length` |
| `AD_REQUIRED_GROUPS` | `ad.required.groups` |

**AUDIT and SYSTEM group constants:**

| Constant | String Value |
|---|---|
| `AUDIT_LOG_RETAIN_DAYS` | `audit.log.retain.days` |
| `AUDIT_REQUEST_LOG_ENABLED` | `audit.request.log.enabled` |
| `SYSTEM_MAINTENANCE_MODE` | `system.maintenance.mode` |
| `SYSTEM_MAX_CONCURRENT_SUBMISSIONS` | `system.max.concurrent.submissions` |
| `SYSTEM_SUBMISSION_TIMEOUT_SECS` | `system.submission.timeout.seconds` |

---

## 8. Usage in Services

**In `CsrValidationService`:**
The service injects `RaConfigCacheService` as a constructor dependency. When validating a CSR, it reads the maximum allowed size using `config.getInt(RaConfigKeys.CSR_MAX_SIZE_BYTES, 8192)` and compares it against the incoming byte length. It reads the allowed signature algorithms using `config.getList(RaConfigKeys.CSR_ALLOWED_SIG_ALGORITHMS)` and checks whether the CSR's algorithm is in the list. All reads are pure cache lookups — no database calls during the request.

**In `AdIdentityFilter`:**
The filter injects `RaConfigCacheService` and, at the start of every request, reads the proxy whitelist using `config.getList(RaConfigKeys.SECURITY_PROXY_WHITELIST_IPS)`. It compares the request's remote IP against the list and rejects the request immediately if the IP is not found. It also reads the maintenance mode flag using `config.getBoolean(RaConfigKeys.SYSTEM_MAINTENANCE_MODE, false)` and returns HTTP 503 if maintenance is active. Both of these checks run on every request and must be extremely fast — the cache read ensures this.

**Key benefit of using the cache in filters:** Because the proxy whitelist is read from the database-backed cache rather than hard-coded in properties, an operator can update the whitelist by modifying the `ra_config` table and calling the admin refresh endpoint, without restarting the service and without any deployment.

---

## 9. Admin Config Cache Refresh API

An admin controller provides endpoints for managing the configuration cache at runtime.

| Method | Path | Description | Response |
|---|---|---|---|
| POST | `/api/ra/ad/admin/config-cache/refresh` | Clears and reloads all active config entries from DB into cache | `{ "status": "refreshed", "activeKeys": 27, "refreshedAt": "..." }` |
| GET | `/api/ra/ad/admin/config-cache/entries` | Returns all currently cached key-value pairs for inspection | Map of config key to config value |

**Security:** All `/admin/**` paths are restricted to users with the `ROLE_RA_ADMIN` authority, enforced by Spring Security method-level security.

---

## 10. Startup Sequence

The configuration cache is populated as part of the application startup. Steps:

1. The JVM starts and Spring Boot initialises the application context.
2. Flyway runs pending migrations. On first startup, `V3__seed_ra_config.sql` creates the `ra_config` table and inserts all 27 default configuration entries.
3. All Spring beans are initialised, including `RaConfigRepository` and `RaConfigCacheService`.
4. `ApplicationReadyEvent` is fired by Spring Boot.
5. `RaConfigCacheService.loadOnStartup()` is triggered by the event. It calls `refresh()`, which queries all active config rows in a single database read and populates the `ConcurrentHashMap`.
6. The service logs the number of active entries loaded (expected: 27 for the Phase 1 seed data).
7. Tomcat begins accepting requests. All subsequent configuration reads are served from the in-memory cache with no database calls.

---

## 11. DB Migration Script

The Flyway migration file `V3__seed_ra_config.sql` performs two operations:

**Table creation:** Creates the `ra_config` table with all required columns, the `UNIQUE` constraint on `config_key`, and the foreign key behaviour described in Section 2. Audit columns inherit their defaults from the table DDL.

**Seed data insertion:** Inserts 27 default configuration rows across all five groups — 7 in the CSR group, 6 in the SECURITY group, 6 in the AD group, 3 in the AUDIT group, and 5 in the SYSTEM group. Each INSERT statement specifies the `config_key`, `config_value`, `config_group`, and `description`. The `is_active` field defaults to true and audit fields use `NOW()` and `'system'` as the inserting identity.

These default values represent the recommended starting configuration. RA administrators should review and update the `security.proxy.whitelist.ips` and `security.cors.allowed.origins` entries immediately after deployment to match their actual network topology.

---

## 12. Summary

| Component | Class / Interface | Purpose |
|---|---|---|
| `ra_config` | DB table | Persistent store for all RA config key-value pairs |
| `RaConfig` | JPA Entity | Maps `ra_config` table, extends `BaseAuditEntity` |
| `RaConfigRepository` | Spring Data JPA | DB access — only used at startup and on refresh |
| `RaConfigCacheService` | `@Service` | In-memory `ConcurrentHashMap` cache, startup load, typed getters |
| `RaConfigKeys` | Constants class | Type-safe key constants — no magic strings in services |
| `RaConfigAdminController` | `@RestController` | Admin endpoints for runtime cache refresh and inspection |
| `V3__seed_ra_config.sql` | Flyway migration | Seeds 27 default configuration entries |
