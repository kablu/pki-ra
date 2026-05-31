# RA AD CSR Service — Testing Strategy

**Document type:** Testing Design
**Module:** `pki-ra/subprojects/ra-ad-csr-service`

---

## 1. Overview

Testing for `ra-ad-csr-service` follows a pyramid model with three levels:

- **Unit Tests** — Test individual classes in isolation with all dependencies mocked. Fast, numerous, no Spring context.
- **Integration Tests** — Test the full Spring context against an H2 in-memory database. Validate filter chains, validation pipelines, service and repository interactions.
- **Slice Tests** — Spring Boot slice annotations (`@WebMvcTest`, `@DataJpaTest`) to test specific layers without spinning up the full application.

All tests run against the `h2` profile. No test ever connects to a real MariaDB instance. Flyway migrations run on the H2 database before each integration test suite, ensuring schema consistency between test and production.

---

## 2. Test Coverage Targets

| Layer | Target Coverage | Rationale |
|---|---|---|
| `CsrValidationService` | 95%+ | Core business logic — every validation branch must be tested |
| `CsrSubmissionService` | 90%+ | Business rules and audit integration |
| `AdIdentityFilter` | 90%+ | Security critical — IP whitelist, header extraction, CRLF |
| `GlobalExceptionHandler` | 85%+ | Every exception type must produce the correct HTTP response |
| `RaConfigCacheService` | 85%+ | Cache load, typed getters, refresh |
| `ErrorCodeCacheService` | 85%+ | Cache load, lookup, fallback |
| Controller layer | 80%+ | Happy path and all error paths |
| Repository layer | 70%+ | JPA queries, duplicate detection |
| Utility / constants | N/A | No logic to test |

---

## 3. Unit Test Design

### 3.1 What is Unit Tested

Unit tests test a single class with all collaborators replaced by Mockito mocks or stubs. No Spring context is loaded — tests run as plain JUnit 5 tests.

| Class | What to Test |
|---|---|
| `CsrValidationService` | Each of the 9 validation stages independently |
| `CsrSubmissionService` | Happy path, identity mismatch, duplicate, audit log calls |
| `AdIdentityFilter` | IP whitelist pass/fail, missing headers, CRLF stripping, maintenance mode |
| `RaConfigCacheService` | `get`, `getInt`, `getBoolean`, `getList`, `getGroup`, `refresh` |
| `ErrorCodeCacheService` | `getError`, `buildErrorResponse`, missing code fallback |
| `GlobalExceptionHandler` | Each exception type → correct HTTP status and body |

### 3.2 Unit Test Structure

Each test class follows the Arrange-Act-Assert pattern:

- **Arrange** — Set up the object under test, configure mocks to return specific values
- **Act** — Call the method being tested
- **Assert** — Verify the return value or side effects (exception thrown, mock method called)

Test method names follow the pattern: `methodName_whenCondition_thenExpectedOutcome`. This makes test failure messages self-documenting.

### 3.3 Mocking Guidelines

- All external dependencies (repositories, cache services, Bouncy Castle wrapper) are mocked
- The clock is injected or wrapped so time-dependent tests are deterministic
- `AuditLogService` is always mocked in service unit tests — audit behaviour is tested separately
- `RaConfigCacheService` is mocked in filter and service unit tests — cache behaviour is tested in its own unit tests

---

## 4. Integration Test Design

### 4.1 What is Integration Tested

Integration tests load the full Spring `ApplicationContext` with the `h2` profile active. Flyway runs all migrations against the H2 database before the context starts. Tests use `MockMvc` (for HTTP layer tests) or inject repositories and services directly.

| Scenario | Integration Test |
|---|---|
| Full CSR submission — happy path | MockMvc POST, verify 201 and response body |
| Duplicate CSR submission | MockMvc POST twice, verify second returns 409 |
| CSR with invalid signature | MockMvc POST with tampered CSR, verify 400 |
| Missing AD header | MockMvc POST without X-AD-Username, verify 400 |
| Untrusted proxy IP | MockMvc POST from non-whitelisted IP, verify 403 |
| Identity mismatch | MockMvc POST with mismatched CN, verify 422 |
| Config cache loads on startup | Verify RaConfigCacheService cache size after context loads |
| Error code cache loads on startup | Verify ErrorCodeCacheService cache size |
| Audit log written on success | Query audit_log after submission, verify record |
| Audit log written on failure | Submit invalid CSR, query audit_log, verify FAILURE record |

### 4.2 Test Database State

Each integration test class annotates with `@Transactional` so every test method runs in a transaction that is rolled back after the test. This ensures tests are isolated — one test's data does not affect another. The base data seeded by Flyway migrations (error codes, config entries) is always present because it is committed by Flyway before the test transaction begins.

### 4.3 MockMvc Request Construction

All integration test HTTP requests are constructed with:
- The correct `Content-Type: application/json` header
- The four required AD identity headers with valid test values
- A `RemoteAddr` set to a whitelisted IP (configured in test application properties)
- A valid or intentionally invalid CSR PEM depending on the scenario

Test CSR data is generated once per test suite using Bouncy Castle with a fixed key pair, ensuring reproducibility. Test PEM strings are stored as constants in a test data class.

---

## 5. Slice Tests

### 5.1 @WebMvcTest — Controller Slice

`@WebMvcTest` loads only the web layer (controllers, filters, security config) without the service or repository layer. Services are mocked. This allows fast controller-level tests that verify:

- Request mapping (correct path and method)
- Request body deserialisation
- Response status codes
- Response body structure
- Security headers present on all responses
- CSRF token requirement enforced

### 5.2 @DataJpaTest — Repository Slice

`@DataJpaTest` loads only the JPA layer with an H2 database. No web or service layer is loaded. This allows testing:

- `CsrSubmissionRepository.findBySubmissionId()` — returns correct record
- `CsrSubmissionRepository.existsByCsrHashAndCreatedAtAfter()` — duplicate detection query
- `RaConfigRepository.findAllByIsActiveTrue()` — filters inactive entries
- `ErrorCodeRepository.findAllByIsActiveTrue()` — filters inactive codes
- `BaseAuditEntity` population — `created_at`, `created_by`, `updated_at`, `updated_by` auto-populated

---

## 6. Test Data Strategy

### 6.1 CSR Test Data

Two categories of test CSRs are used:

**Valid CSRs:**
- RSA-2048 with SHA256withRSA — standard valid CSR
- RSA-4096 with SHA512withRSA — larger key valid CSR
- EC P-256 with SHA256withECDSA — elliptic curve valid CSR

Each valid CSR is generated with a known test key pair and a known subject CN (`CN=Test User`). The corresponding private key is discarded after generation — only the PEM is kept.

**Invalid CSRs:**
- Tampered CSR — valid PEM structure but signature verification fails (one byte changed in the signature)
- Wrong algorithm CSR — signed with SHA1withRSA (not in allowed list)
- Small key CSR — signed with RSA-1024 (not in allowed list)
- Truncated PEM — PEM header present but content cut off
- Random bytes — not a PEM at all

### 6.2 AD Header Test Data

A fixed set of test AD identity values is used across all tests:

| Header | Test Value |
|---|---|
| `X-AD-Username` | `testuser` |
| `X-AD-DisplayName` | `Test User` |
| `X-AD-Email` | `testuser@ra.internal` |
| `X-AD-Groups` | `RA-Users,PKI-Submitters` |

For identity mismatch tests, the CSR CN is set to `CN=Other User` while the AD headers contain `testuser` / `Test User`.

### 6.3 Configuration Test Data

The H2 test instance has the same seed data as production (inserted by Flyway V2 and V3 migrations). The proxy IP whitelist in the test config includes `127.0.0.1` so MockMvc requests (which originate from localhost) pass the IP check without special handling.

---

## 7. What NOT to Mock

The following must use real implementations in integration tests:

| Component | Why real |
|---|---|
| H2 DataSource | Verifies Flyway schema and JPA mapping correctness |
| Flyway | Verifies all migration scripts are valid SQL |
| `AuditLogService` | Verifies REQUIRES_NEW commits independently |
| `RaConfigCacheService` | Verifies startup cache load from DB |
| `ErrorCodeCacheService` | Verifies startup cache load from DB |
| Bouncy Castle CSR parsing | Verifies actual cryptographic behaviour |
| Spring Security filter chain | Verifies real security headers and CSRF behaviour |

Mocking any of the above in integration tests defeats the purpose of integration testing. The value of an integration test is precisely that these components work together correctly.

---

## 8. Test Execution

| Command | What runs |
|---|---|
| `./gradlew :ra-ad-csr-service:test` | All unit and integration tests |
| `./gradlew :ra-ad-csr-service:test --tests "*.unit.*"` | Unit tests only |
| `./gradlew :ra-ad-csr-service:test --tests "*.integration.*"` | Integration tests only |
| `./gradlew :ra-ad-csr-service:jacocoTestReport` | Generate HTML coverage report |

---

## 9. CI Pipeline Test Requirements

The following gates must pass before any code is merged to the main branch:

| Gate | Requirement |
|---|---|
| All tests pass | Zero test failures |
| Coverage threshold | Minimum 80% line coverage on application source (excluding DTOs and constants) |
| No skipped tests | `@Disabled` annotations require a linked ticket in a comment |
| Integration tests run | CI must not skip integration tests for speed — they are mandatory |
| Flyway migration validates | `./gradlew flywayValidate` must pass on the H2 test database |
