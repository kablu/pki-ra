# RA AD CSR Service — Common Bean Reuse Document

**Document type:** Bean Reuse from `:common` Module
**Module:** `pki-ra/subprojects/common`
**Consumer:** `ra-ad-csr-service`

---

## 1. Overview

The `ra-ad-csr-service` module reuses **all shared infrastructure beans** declared in the `:common` module with **zero re-declaration**. This eliminates code duplication across RA service modules and ensures consistent datasource, auditing, and exception handling behaviour.

The single enablement mechanism is the `scanBasePackages` attribute on the `@SpringBootApplication` annotation set to `"com.pki.ra"`. This causes Spring to scan both the `com.pki.ra.common.*` package tree, discovering all `:common` beans, and the `com.pki.ra.adcsr.*` package tree, discovering all beans local to this service. No `@Import`, no `@ComponentScan` duplication, and no `@Bean` re-definition is required anywhere in this module.

---

## 2. Bean Reuse Map

### 2.1 `DataSourceConfig` — H2 DataSource

**Class:** `com.pki.ra.common.config.DataSourceConfig`
**Profile:** `h2` only

**What it provides:**
The `DataSourceConfig` class is annotated with `@Configuration` and `@Profile("h2")`. It declares a `@Bean @Primary DataSource` method that creates a `HikariDataSource` backed by an H2 in-memory engine. All pool parameters are loaded from a file named `db.properties` on the classpath, read via a `@PropertySource` annotation, and bound into a `DatabaseProperties` configuration properties bean.

**How `ra-ad-csr-service` uses it:**
The `DataSource` bean is auto-detected by Spring Boot's JPA auto-configuration and injected into the `LocalContainerEntityManagerFactoryBean`. No configuration is needed in this module — Spring Boot does it automatically once the `DataSource` bean is present on the application context.

**What `ra-ad-csr-service` must provide:**
The module must include a `db.properties` file in `src/main/resources/`. This file must contain the JDBC URL, username, password, driver class name, and Hikari pool settings. The full list of required properties and their values for both H2 (development) and MariaDB (production) is documented in the Deployment and Profile Configuration section (Section 11).

---

### 2.2 `DatabaseConfig` — JPA Auditing and Transaction Management

**Class:** `com.pki.ra.common.config.DatabaseConfig`
**Profile:** All profiles (no `@Profile` restriction)

**What it provides:**
The `DatabaseConfig` class enables three critical JPA capabilities across the entire RA platform. It activates `@EnableTransactionManagement`, which makes the `@Transactional` annotation work on any Spring bean in any module. It activates `@EnableJpaAuditing` referencing the `auditorAwareImpl` bean name, which causes Spring Data to automatically populate audit columns on every entity save. It activates `@EnableJpaRepositories` with `basePackages = "com.pki.ra"`, which causes Spring Data to discover every `JpaRepository` sub-interface in any RA module, including `CsrSubmissionRepository` in this module.

**How `ra-ad-csr-service` uses it:**
- `@EnableTransactionManagement` makes `@Transactional` on `CsrSubmissionService` work.
- `@EnableJpaAuditing` populates `created_at`, `created_by`, `updated_at`, `updated_by` on `CsrSubmissionRecord` (which extends `BaseAuditEntity`) automatically on every INSERT and UPDATE.
- `@EnableJpaRepositories(basePackages = "com.pki.ra")` discovers `CsrSubmissionRepository` without any additional annotation.

**No action required** in `ra-ad-csr-service` — `DatabaseConfig` is activated automatically by the component scan.

---

### 2.3 `AuditorAwareImpl` — Current AD Username for Audit Fields

**Class:** `com.pki.ra.common.config.AuditorAwareImpl`
**Bean name:** `"auditorAwareImpl"`
**Interface:** `AuditorAware<String>`

**What it provides:**
`AuditorAwareImpl` implements the `AuditorAware<String>` interface and overrides `getCurrentAuditor()`. The method reads the current principal from the Spring Security `SecurityContextHolder`. If a non-anonymous `Authentication` is present, it returns the principal's name (the AD sAMAccountName). If the context contains an anonymous user or is empty, it falls back to returning `"system"`, which is used for background jobs and Flyway seed migrations.

**How `ra-ad-csr-service` uses it:**
Since the service uses the `AdIdentityFilter` (not Spring Security form login or OAuth), the Security context would otherwise contain an anonymous user after the filter runs. To make `AuditorAwareImpl` return the correct AD username, the `AdIdentityFilter` must set a `UsernamePasswordAuthenticationToken` in the `SecurityContextHolder` after building the `AdIdentity`. The token is constructed with the AD display name as the principal, a null credentials value, and an empty authorities list. Once this token is set, `AuditorAwareImpl` returns the display name, which is then automatically written to `created_by` and `updated_by` on every `CsrSubmissionRecord` save.

**Option B (not recommended):** Override `AuditorAwareImpl` by providing a local bean with the same name. This requires enabling `spring.main.allow-bean-definition-overriding=true`, which undermines the contract of the shared `:common` module and is not recommended.

---

### 2.4 `AuditLogService` — Persistent Audit Trail

**Class:** `com.pki.ra.common.util.AuditLogService`
**Annotation:** `@Service`
**Transaction:** `@Transactional(propagation = REQUIRES_NEW)` on all methods

**What it provides:**

| Method | Description |
|---|---|
| `logSuccess(username, action, resourceId, description, ipAddress)` | Logs SUCCESS outcome |
| `logFailure(username, action, resourceId, description, ipAddress)` | Logs FAILURE outcome |
| `log(username, action, resourceId, description, ipAddress, outcome)` | Full control |

**How `ra-ad-csr-service` uses it:**
`CsrSubmissionService` declares `AuditLogService` as a constructor-injected dependency. On a successful CSR submission, it calls `logSuccess()` with the action `CSR_SUBMIT` and the newly assigned `submissionId`. On any failure path (duplicate, validation error, persistence error), it calls `logFailure()` with the appropriate action constant before re-throwing the exception.

**Key behaviour:** The `REQUIRES_NEW` propagation means the audit record is committed to the database **even if the main transaction rolls back**. The audit trail is always complete, regardless of business logic exceptions.

**Action constants used by `ra-ad-csr-service`:**

| Constant | Trigger |
|---|---|
| `CSR_SUBMIT` | Successful CSR submission |
| `CSR_SUBMIT_DUPLICATE` | Rejected — duplicate CSR hash |
| `CSR_SUBMIT_INVALID` | Rejected — validation failure |
| `CSR_SUBMIT_IDENTITY_MISMATCH` | Rejected — CN vs AD user mismatch |
| `CSR_SUBMIT_UNAUTHORIZED` | Rejected — untrusted proxy |

---

### 2.5 `BaseAuditEntity` — Automatic Audit Columns

**Class:** `com.pki.ra.common.model.BaseAuditEntity`
**Annotation:** `@MappedSuperclass`, `@EntityListeners(AuditingEntityListener.class)`

**What it provides:**

| Column | JPA Annotation | Description |
|---|---|---|
| `created_at` | `@CreatedDate` | Populated on INSERT |
| `created_by` | `@CreatedBy` | AD username from `AuditorAwareImpl` |
| `updated_at` | `@LastModifiedDate` | Updated on every UPDATE |
| `updated_by` | `@LastModifiedBy` | AD username on last modification |

**How `ra-ad-csr-service` uses it:**
`CsrSubmissionRecord` is declared as a JPA entity that extends `BaseAuditEntity`. By extending this class, the entity automatically inherits all four audit columns as JPA-mapped fields. No additional annotations or field declarations are needed in `CsrSubmissionRecord` — Spring Data JPA Auditing handles all four columns automatically on every save and update.

---

### 2.6 `AuditLog` — Audit Log Entity

**Class:** `com.pki.ra.common.model.AuditLog`

**What it provides:**
The `AuditLog` class is a JPA entity mapped to the `audit_log` table. It is used exclusively by `AuditLogService`. The `ra-ad-csr-service` does **not** directly manage `AuditLog` entities — it only calls `AuditLogService` methods and the service handles all persistence. The table schema is managed by the `:common` module's Flyway migration. The full column definitions are documented in the Database Design section (Section 7).

---

### 2.7 `RequestLog` — Inbound Request Log Entity

**Class:** `com.pki.ra.common.model.RequestLog`

**How `ra-ad-csr-service` uses it (optional):**
`RequestLog` is a JPA entity mapped to the `request_log` table. In `ra-ad-csr-service`, it can optionally be used in a request logging filter to record every inbound HTTP request for diagnostics. The filter would build a `RequestLog` instance containing the HTTP method, request path, source IP, and timestamp, then persist it via the entity manager. This is optional for Phase 1 — the `AuditLog` written via `AuditLogService` is the primary audit trail. The `audit.request.log.enabled` configuration key controls whether the optional request logging is active.

---

### 2.8 `PkiRaException` — Base Exception

**Class:** `com.pki.ra.common.exception.PkiRaException`

**What it provides:**
`PkiRaException` extends `RuntimeException` and carries an `HttpStatus` field. It is the base class for all domain exceptions in the RA platform. The `GlobalExceptionHandler` catches `PkiRaException` and uses the `status` field to set the HTTP response status, so no per-exception `@ExceptionHandler` method is needed for each specific subclass.

**How `ra-ad-csr-service` uses it:**
All local exceptions extend `PkiRaException` with a fixed HTTP status:

- `CsrValidationException` — extends `PkiRaException` with HTTP 400
- `CsrIdentityMismatchException` — extends `PkiRaException` with HTTP 422
- `DuplicateCsrException` — extends `PkiRaException` with HTTP 409
- `UnauthorizedProxyException` — extends `PkiRaException` with HTTP 403

---

### 2.9 `ResourceNotFoundException`

**Class:** `com.pki.ra.common.exception.ResourceNotFoundException`

Used when a submission is looked up by `submissionId` but not found in the repository. The `CsrSubmissionService` calls `repository.findBySubmissionId(id)` and uses the `orElseThrow()` pattern to throw this exception if the result is empty. The `GlobalExceptionHandler` maps it to HTTP 404.

---

## 3. What `ra-ad-csr-service` Must NOT Re-Declare

The following beans **must not** be redeclared in `ra-ad-csr-service`. Redeclaring them would cause `BeanDefinitionOverrideException` at startup unless `spring.main.allow-bean-definition-overriding=true` is set, which is explicitly disabled in `application.properties` for this module.

| Bean | Class in :common | Do NOT redeclare |
|---|---|---|
| `dataSource` | `DataSourceConfig.dataSource()` | ✗ |
| `auditorAwareImpl` | `AuditorAwareImpl` | ✗ |
| `auditLogService` | `AuditLogService` | ✗ |
| `databaseConfig` | `DatabaseConfig` | ✗ |

---

## 4. Dependency Declaration in `build.gradle`

The `:common` module is included in the `ra-ad-csr-service` Gradle build file with a single `implementation project(':common')` declaration in the `dependencies` block. Spring Boot's auto-configuration and the `scanBasePackages = "com.pki.ra"` setting on the application entry point do the rest — no further configuration is needed in this module to activate any of the shared beans.

---

## 5. Summary Table

| `:common` Bean / Class | Used by `ra-ad-csr-service` | Purpose | Action needed |
|---|---|---|---|
| `DataSourceConfig.dataSource()` | JPA / HikariCP | H2 / MariaDB connection pool | Provide `db.properties` |
| `DatabaseConfig` | Spring Data JPA | Transactions, auditing, repo scan | None |
| `AuditorAwareImpl` | JPA Auditing | `created_by` / `updated_by` columns | Set auth in `AdIdentityFilter` |
| `AuditLogService` | `CsrSubmissionService` | Persistent audit trail | Inject and call |
| `BaseAuditEntity` | `CsrSubmissionRecord` | Audit columns inheritance | Extend the class |
| `AuditLog` | via `AuditLogService` | Audit log table entity | No direct use |
| `RequestLog` | Optional filter | Request log table entity | Optional use |
| `PkiRaException` | All local exceptions | Base exception with HTTP status | Extend for local exceptions |
| `ResourceNotFoundException` | `CsrSubmissionService` | 404 for missing submissions | Use directly |
| `DatabaseProperties` | via `DataSourceConfig` | DB config binding | Provide `db.properties` |
