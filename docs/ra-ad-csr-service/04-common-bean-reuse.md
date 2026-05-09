# RA AD CSR Service — Common Bean Reuse Document

**Document type:** Bean Reuse from `:common` Module
**Module:** `pki-ra/subprojects/common`
**Consumer:** `ra-ad-csr-service`

---

## 1. Overview

The `ra-ad-csr-service` module reuses **all shared infrastructure beans** declared in the `:common` module with **zero re-declaration**. This eliminates code duplication across RA service modules and ensures consistent datasource, auditing, and exception handling behaviour.

The single enablement mechanism is:

```java
@SpringBootApplication(scanBasePackages = "com.pki.ra")
public class AdCsrServiceApplication { ... }
```

`scanBasePackages = "com.pki.ra"` causes Spring to scan both:
- `com.pki.ra.common.*` — all `:common` beans
- `com.pki.ra.adcsr.*` — all beans local to this service

No `@Import`, no `@ComponentScan` duplication, no `@Bean` re-definition.

---

## 2. Bean Reuse Map

### 2.1 `DataSourceConfig` — H2 DataSource

**Class:** `com.pki.ra.common.config.DataSourceConfig`
**Profile:** `h2` only

**What it provides:**
```
@Bean @Primary DataSource dataSource()
  → HikariDataSource backed by H2 in-memory engine
  → Loaded from classpath:db.properties via @PropertySource
  → All pool parameters driven by DatabaseProperties
```

**How `ra-ad-csr-service` uses it:**
- The `DataSource` bean is auto-injected into Spring Data JPA's `LocalContainerEntityManagerFactoryBean`
- No code needed — Spring Boot auto-configuration detects the `DataSource` bean

**What `ra-ad-csr-service` must provide:**
```
src/main/resources/db.properties   ← copy from :common or raservice pattern
```

Example `db.properties` for H2:
```properties
db.datasource.url=jdbc:h2:mem:ra_ad_csr;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
db.datasource.username=sa
db.datasource.password=
db.datasource.driver-class-name=org.h2.Driver
db.datasource.hikari.pool-name=RA-AD-CSR-Pool
db.datasource.hikari.maximum-pool-size=5
db.datasource.hikari.minimum-idle=1
db.datasource.hikari.connection-timeout=20000
db.datasource.hikari.idle-timeout=300000
db.datasource.hikari.max-lifetime=900000
db.datasource.hikari.auto-commit=true
db.h2.console.enabled=true
db.h2.console.path=/h2-console
db.h2.console.settings.web-allow-others=false
db.h2.console.settings.trace=false
```

**For production (MariaDB profile):**
```properties
db.datasource.url=jdbc:mariadb://mariadb-host:3306/ra_ad_csr_db
db.datasource.username=${DB_USERNAME}
db.datasource.password=${DB_PASSWORD}
db.datasource.driver-class-name=org.mariadb.jdbc.Driver
```

---

### 2.2 `DatabaseConfig` — JPA Auditing and Transaction Management

**Class:** `com.pki.ra.common.config.DatabaseConfig`
**Profile:** All profiles (no `@Profile` restriction)

**What it provides:**
```java
@Configuration
@EnableTransactionManagement          ← Enables @Transactional in all beans
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")  ← Wires AuditorAwareImpl
@EnableJpaRepositories(basePackages = "com.pki.ra")       ← Scans ALL RA repositories
```

**How `ra-ad-csr-service` uses it:**
- `@EnableTransactionManagement` makes `@Transactional` on `CsrSubmissionService` work
- `@EnableJpaAuditing` populates `created_at`, `created_by`, `updated_at`, `updated_by`
  on `CsrSubmissionRecord` (which extends `BaseAuditEntity`) automatically
- `@EnableJpaRepositories(basePackages = "com.pki.ra")` discovers `CsrSubmissionRepository`

**No action required** in `ra-ad-csr-service` — `DatabaseConfig` is activated automatically
by component scan.

---

### 2.3 `AuditorAwareImpl` — Current AD Username for Audit Fields

**Class:** `com.pki.ra.common.config.AuditorAwareImpl`
**Bean name:** `"auditorAwareImpl"`
**Interface:** `AuditorAware<String>`

**What it provides:**
```java
@Override
public Optional<String> getCurrentAuditor() {
    // Returns AD sAMAccountName from Spring Security context
    // Falls back to "system" for background jobs / migrations
}
```

**How `ra-ad-csr-service` uses it:**

Since the service uses the `AdIdentityFilter` (not Spring Security authentication),
the Security context will have an anonymous user. Two options:

**Option A (Recommended for Phase 1):** After extracting `AdIdentity` in `AdIdentityFilter`,
set a `UsernamePasswordAuthenticationToken` in the `SecurityContext`:

```java
// In AdIdentityFilter, after building AdIdentity:
UsernamePasswordAuthenticationToken auth =
    new UsernamePasswordAuthenticationToken(
        identity.displayName(), null, List.of());
SecurityContextHolder.getContext().setAuthentication(auth);
```

This causes `AuditorAwareImpl` to return the AD username automatically,
which then populates `created_by` / `updated_by` on `CsrSubmissionRecord`.

**Option B:** Override `AuditorAwareImpl` by providing a local bean with the same name
(Spring Boot allows overriding with `spring.main.allow-bean-definition-overriding=true`).
**Not recommended** — breaks the shared contract.

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

In `CsrSubmissionService`:
```java
@RequiredArgsConstructor
public class CsrSubmissionService {

    private final AuditLogService auditLogService; // injected from :common

    public CsrSubmitResponse submit(...) {
        try {
            // ... business logic ...
            auditLogService.logSuccess(
                identity.displayName(),   // username
                "CSR_SUBMIT",             // action
                submissionId,             // resourceId
                "CSR submitted for profile " + profile,  // description
                sourceIp                  // ipAddress
            );
        } catch (Exception ex) {
            auditLogService.logFailure(
                identity.displayName(),
                "CSR_SUBMIT",
                null,
                ex.getMessage(),
                sourceIp
            );
            throw ex;
        }
    }
}
```

**Key behaviour:** `REQUIRES_NEW` propagation means the audit record is committed to the
database **even if the main transaction rolls back**. The audit trail is always complete.

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

```java
@Entity
@Table(name = "csr_submission")
public class CsrSubmissionRecord extends BaseAuditEntity {
    // created_at, created_by, updated_at, updated_by — inherited, no code needed
    // All four columns are automatically populated by Spring Data JPA Auditing
}
```

**Database DDL result:**
```sql
CREATE TABLE csr_submission (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id       VARCHAR(36)  NOT NULL UNIQUE,
    -- ... other columns ...
    created_at          TIMESTAMP    NOT NULL,
    created_by          VARCHAR(100) NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    updated_by          VARCHAR(100) NOT NULL
);
```

---

### 2.6 `AuditLog` — Audit Log Entity

**Class:** `com.pki.ra.common.model.AuditLog`

**What it provides:**

The `audit_log` table entity, persisted by `AuditLogService`.
`ra-ad-csr-service` does **not** directly manage `AuditLog` entities —
it only calls `AuditLogService` methods and the service handles persistence.

**Schema written by `:common`:**
```sql
CREATE TABLE audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(100)  NOT NULL,
    action      VARCHAR(100)  NOT NULL,
    resource_id VARCHAR(200),
    description VARCHAR(1000),
    ip_address  VARCHAR(50),
    outcome     VARCHAR(20)   NOT NULL DEFAULT 'SUCCESS',
    created_at  TIMESTAMP     NOT NULL
);
```

---

### 2.7 `RequestLog` — Inbound Request Log Entity

**Class:** `com.pki.ra.common.model.RequestLog`

**How `ra-ad-csr-service` uses it (optional):**

Can be used in a `RequestLoggingFilter` to record every inbound request for diagnostics:

```java
// In a filter or interceptor:
RequestLog log = RequestLog.builder()
    .req("POST /api/ra/ad/csr/submit from " + sourceIp)
    .reqTs(LocalDateTime.now())
    .build();
entityManager.persist(log);
```

This is optional for Phase 1 — the `AuditLog` via `AuditLogService` is the primary trail.

---

### 2.8 `PkiRaException` — Base Exception

**Class:** `com.pki.ra.common.exception.PkiRaException`

**What it provides:**
```java
public class PkiRaException extends RuntimeException {
    private final HttpStatus status;
    // ...
}
```

**How `ra-ad-csr-service` uses it:**

All local exceptions extend `PkiRaException`:
```java
// CsrValidationException       extends PkiRaException (400)
// CsrIdentityMismatchException extends PkiRaException (422)
// DuplicateCsrException        extends PkiRaException (409)
// UnauthorizedProxyException   extends PkiRaException (403)
```

`GlobalExceptionHandler` catches `PkiRaException` and uses `ex.getStatus()` to set
the HTTP response status — no per-exception `@ExceptionHandler` needed.

---

### 2.9 `ResourceNotFoundException`

**Class:** `com.pki.ra.common.exception.ResourceNotFoundException`

Used when a submission is looked up by `submissionId` but not found:

```java
public CsrSubmissionRecord findBySubmissionId(String id) {
    return repository.findBySubmissionId(id)
        .orElseThrow(() -> new ResourceNotFoundException(
            "CSR submission not found: " + id));
}
```

---

## 3. What `ra-ad-csr-service` Must NOT Re-Declare

The following beans **must not** be redeclared in `ra-ad-csr-service`.
Redeclaring them would cause `BeanDefinitionOverrideException` unless
`spring.main.allow-bean-definition-overriding=true` is set (not recommended).

| Bean | Class in :common | Do NOT redeclare |
|---|---|---|
| `dataSource` | `DataSourceConfig.dataSource()` | ✗ |
| `auditorAwareImpl` | `AuditorAwareImpl` | ✗ |
| `auditLogService` | `AuditLogService` | ✗ |
| `databaseConfig` | `DatabaseConfig` | ✗ |

---

## 4. Dependency Declaration in `build.gradle`

The single line that makes all of the above available:

```groovy
dependencies {
    implementation project(':common')
    // ...
}
```

Spring Boot's auto-configuration and `scanBasePackages = "com.pki.ra"` do the rest.

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
