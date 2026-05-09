# RA AD CSR Service — API & Development Specification

**Document type:** Controller · Service · Repository Specification
**Audience:** Developers starting implementation
**Service:** `ra-ad-csr-service`

---

## 1. Gradle Module Setup

### `build.gradle`

```groovy
plugins {
    id 'org.springframework.boot'
    id 'io.spring.dependency-management'
    id 'java'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

dependencies {
    // ── Common module (bean reuse) ─────────────────────────────────────────
    implementation project(':common')

    // ── Spring Boot ────────────────────────────────────────────────────────
    implementation libs.spring.boot.starter.web
    implementation libs.spring.boot.starter.security
    implementation libs.spring.boot.starter.data.jpa
    implementation libs.spring.boot.starter.validation
    implementation libs.spring.boot.starter.actuator

    // ── Cryptography ───────────────────────────────────────────────────────
    implementation libs.bouncycastle.provider    // bcprov-jdk18on
    implementation libs.bouncycastle.pkix        // bcpkix-jdk18on

    // ── Input sanitization ─────────────────────────────────────────────────
    implementation 'com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20240325.1'

    // ── API docs ───────────────────────────────────────────────────────────
    implementation libs.springdoc.openapi.starter

    // ── Lombok ─────────────────────────────────────────────────────────────
    compileOnly libs.lombok
    annotationProcessor libs.lombok

    // ── Runtime ────────────────────────────────────────────────────────────
    runtimeOnly libs.h2
    runtimeOnly libs.mariadb.driver

    // ── Test ───────────────────────────────────────────────────────────────
    testImplementation libs.spring.boot.starter.test
    testImplementation libs.spring.security.test
}
```

---

## 2. Application Entry Point

### `AdCsrServiceApplication.java`

```java
package com.pki.ra.adcsr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.pki.ra")
public class AdCsrServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdCsrServiceApplication.class, args);
    }
}
```

> **Critical:** `scanBasePackages = "com.pki.ra"` ensures all `:common` Spring beans
> (`DataSourceConfig`, `DatabaseConfig`, `AuditorAwareImpl`, `AuditLogService`) are discovered
> without any re-declaration. See Document 04 for details.

---

## 3. Controller Specification

### `CsrSubmitController.java`

**Package:** `com.pki.ra.adcsr.controller`
**Mapping:** `POST /api/ra/ad/csr/submit`

```
@Tag(name = "AD CSR Submission")
@RestController
@RequestMapping("/api/ra/ad/csr")
@RequiredArgsConstructor
@Slf4j
```

#### Method: `submit`

| Attribute | Value |
|---|---|
| HTTP Method | `POST` |
| Path | `/submit` |
| Consumes | `application/json` |
| Produces | `application/json` |
| Response success | `202 Accepted` |

**Parameters:**

| Parameter | Source | Type | Required | Description |
|---|---|---|---|---|
| `adIdentity` | Request attribute `"AD_IDENTITY"` | `AdIdentity` | Yes | Set by `AdIdentityFilter` |
| `request` | Request body | `@Valid CsrSubmitRequest` | Yes | Contains `pkcs10_pem` |
| `httpRequest` | `HttpServletRequest` | — | Yes | For source IP extraction |

**Method signature:**
```java
@PostMapping("/submit")
@Operation(summary = "Submit a PKCS#10 CSR authenticated via Active Directory")
public ResponseEntity<CsrSubmitResponse> submit(
    @RequestAttribute("AD_IDENTITY") AdIdentity adIdentity,
    @Valid @RequestBody CsrSubmitRequest request,
    HttpServletRequest httpRequest
);
```

**Logic (controller only — no business logic):**
```
1. log.info("CSR submit — user={}, ip={}", adIdentity.displayName(), sourceIp)
2. CsrSubmitResponse response = csrSubmissionService.submit(adIdentity, request, sourceIp)
3. return ResponseEntity.accepted().body(response)
```

**Swagger responses:**
- `202` — CSR accepted, `CsrSubmitResponse` body
- `400` — Validation failed (header/CSR invalid)
- `401` — Missing AD identity header
- `403` — Request not from trusted proxy
- `409` — Duplicate CSR already submitted
- `422` — CSR identity does not match AD user

---

## 4. DTO Specifications

### `CsrSubmitRequest.java`

```java
package com.pki.ra.adcsr.dto;

@Schema(description = "CSR submission request from an AD-authenticated user")
public class CsrSubmitRequest {

    @NotBlank(message = "pkcs10_pem must not be blank")
    @Size(max = 8192, message = "pkcs10_pem must not exceed 8192 characters")
    @ValidCsrPem
    @JsonProperty("pkcs10_pem")
    @Schema(description = "PKCS#10 CSR in PEM format", requiredMode = REQUIRED)
    private String pkcs10Pem;

    @Pattern(
        regexp = "DSC|DOCUMENT_SIGNING|CODE_SIGNING|TLS_CLIENT|TLS_SERVER|EMAIL_ENCRYPT",
        message = "certificate_profile must be one of: DSC, DOCUMENT_SIGNING, CODE_SIGNING, " +
                  "TLS_CLIENT, TLS_SERVER, EMAIL_ENCRYPT"
    )
    @JsonProperty("certificate_profile")
    @Schema(description = "Requested certificate profile. Defaults to DSC.", nullable = true)
    private String certificateProfile;

    @Min(value = 1,    message = "validity_days must be at least 1")
    @Max(value = 3650, message = "validity_days must not exceed 3650 (10 years)")
    @JsonProperty("validity_days")
    @Schema(description = "Requested validity in days. Defaults to 365.", nullable = true)
    private Integer validityDays;

    @Size(max = 500, message = "remarks must not exceed 500 characters")
    @JsonProperty("remarks")
    @Schema(description = "Optional applicant note. Max 500 characters.", nullable = true)
    private String remarks;
}
```

---

### `CsrSubmitResponse.java`

```java
package com.pki.ra.adcsr.dto;

@Schema(description = "Confirmation of CSR submission acceptance")
@JsonPropertyOrder({"submission_id", "status", "submitted_by", "submitted_at", "message"})
public class CsrSubmitResponse {

    @JsonProperty("submission_id")
    @Schema(description = "UUID identifying this submission — use for status queries",
            example = "3f7a1c2e-9b4d-4f2a-8e1c-5d6f7a8b9c0d")
    private String submissionId;

    @JsonProperty("status")
    @Schema(description = "Current status of the submission", example = "PENDING")
    private String status;   // always PENDING at submission time

    @JsonProperty("submitted_by")
    @Schema(description = "AD username or email of the submitting user",
            example = "rahul.kumar")
    private String submittedBy;

    @JsonProperty("submitted_at")
    @Schema(description = "ISO-8601 timestamp of submission", example = "2026-05-09T08:30:00Z")
    private String submittedAt;

    @JsonProperty("message")
    @Schema(description = "Human-readable confirmation message")
    private String message;
}
```

---

### `AdIdentity.java` (Record)

```java
package com.pki.ra.adcsr.dto;

public record AdIdentity(
    String username,   // sAMAccountName — nullable if only email present
    String email,      // UPN / email    — nullable if only username present
    String sourceIp    // client IP from X-Forwarded-For or RemoteAddr
) {
    public String displayName() {
        return username != null ? username : email;
    }

    public boolean hasUsername() { return username != null && !username.isBlank(); }
    public boolean hasEmail()    { return email    != null && !email.isBlank();    }
}
```

---

## 5. Filter Specification

### `AdIdentityFilter.java`

**Package:** `com.pki.ra.adcsr.filter`
**Extends:** `OncePerRequestFilter`

```
Fields:
  - String usernameHeader    (from config: ra.ad.header.username)
  - String emailHeader       (from config: ra.ad.header.email)
  - Set<String> trustedIps   (from TrustedProxyConfig)

doFilterInternal(request, response, chain):

  Step 1 — Proxy IP validation
    clientIp = resolveClientIp(request)
    if NOT trustedIps.contains(clientIp):
      throw UnauthorizedProxyException("Request not from trusted AD proxy")

  Step 2 — Header extraction
    username = sanitizeHeader(request.getHeader(usernameHeader))
    email    = sanitizeHeader(request.getHeader(emailHeader))

  Step 3 — Presence check
    if username is blank AND email is blank:
      response.sendError(401, "Missing AD identity header")
      return

  Step 4 — CRLF / injection check
    if username or email contains [\r\n\0]:
      throw CsrValidationException("Invalid characters in identity header")

  Step 5 — Store identity and continue
    AdIdentity identity = new AdIdentity(username, email, clientIp)
    request.setAttribute("AD_IDENTITY", identity)
    chain.doFilter(request, response)

resolveClientIp(request):
  xForwardedFor = request.getHeader("X-Forwarded-For")
  if blank: return request.getRemoteAddr()
  return xForwardedFor.split(",")[0].strip()   // outermost hop

sanitizeHeader(value):
  if blank: return null
  return value.strip()
```

---

## 6. Validation Specifications

### 6.1 `HeaderValidator.java`

**Package:** `com.pki.ra.adcsr.validation`

```
USERNAME_PATTERN = ^[a-zA-Z0-9._\-]{1,64}$
EMAIL_PATTERN    = ^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$

validate(AdIdentity identity):
  if identity.hasUsername():
    assert USERNAME_PATTERN matches identity.username
      else throw CsrValidationException("Invalid username format in X-AD-Username header")
    assert identity.username.length() <= 64
      else throw CsrValidationException("Username exceeds 64 characters")

  if identity.hasEmail():
    assert EMAIL_PATTERN matches identity.email
      else throw CsrValidationException("Invalid email format in X-AD-Email header")
    assert identity.email.length() <= 254
      else throw CsrValidationException("Email exceeds 254 characters")
```

---

### 6.2 `Pkcs10CsrValidator.java`

**Package:** `com.pki.ra.adcsr.validation`
**Dependency:** `org.bouncycastle.pkcs.PKCS10CertificationRequest`

```
validate(String pkcs10Pem) → PKCS10CertificationRequest:

  Step 1 — Size check
    byte[] rawBytes = pkcs10Pem.getBytes(UTF_8)
    if rawBytes.length > maxCsrSizeBytes (16384):
      throw CsrValidationException("CSR exceeds maximum allowed size of 16 KB")

  Step 2 — Parse
    PKCS10CertificationRequest csr = parse(pkcs10Pem)
    // PEM: PEMParser → PKCS10CertificationRequest
    // base64-DER: Base64.getMimeDecoder().decode(...) → new PKCS10CertificationRequest(der)
    if parse fails:
      throw CsrValidationException("CSR is not a valid PKCS#10 request: " + cause)

  Step 3 — Signature verification
    ContentVerifierProvider provider = new JcaContentVerifierProviderBuilder()
        .setProvider("BC").build(csr.getSubjectPublicKeyInfo())
    if NOT csr.isSignatureValid(provider):
      throw CsrValidationException("CSR self-signature verification failed")

  Step 4 — Algorithm and key strength
    AlgorithmIdentifier keyAlgo = csr.getSubjectPublicKeyInfo().getAlgorithm()
    if RSA:
      RSAKeyParameters params = (RSAKeyParameters) PublicKeyFactory.createKey(spki)
      if params.getModulus().bitLength() < minRsaKeyBits (2048):
        throw CsrValidationException("RSA key must be at least 2048 bits")
    else if ECDSA:
      ECNamedCurveSpec spec = ...
      if spec.getName() NOT IN allowedEcCurves:
        throw CsrValidationException("EC curve not allowed: " + curveName)
    else:
      throw CsrValidationException("Unsupported key algorithm: " + keyAlgo.getAlgorithm())

  Step 5 — Subject CN presence
    X500Name subject = csr.getSubject()
    String cn = extractCN(subject)
    if cn is blank:
      throw CsrValidationException("CSR Subject must contain a non-blank CN")

  return csr
```

---

### 6.3 `CsrIdentityMatcher.java`

**Package:** `com.pki.ra.adcsr.validation`

```
match(AdIdentity identity, PKCS10CertificationRequest csr):

  subjectCN   = extractCN(csr.getSubject()).toLowerCase()
  sanEmails   = extractSanEmails(csr).stream().map(String::toLowerCase).toList()

  adUsername  = identity.hasUsername() ? identity.username().toLowerCase() : null
  adEmail     = identity.hasEmail()    ? identity.email().toLowerCase()    : null

  matched = false

  if adEmail != null:
    if subjectCN.equals(adEmail)       → matched = true
    if sanEmails.contains(adEmail)     → matched = true

  if adUsername != null:
    if subjectCN.equals(adUsername)    → matched = true

  if NOT matched:
    throw CsrIdentityMismatchException(
      "CSR Subject CN '" + subjectCN + "' does not match AD identity '" + identity.displayName() + "'"
    )

extractCN(X500Name name):
  return Arrays.stream(name.getRDNs(BCStyle.CN))
    .flatMap(rdn -> Arrays.stream(rdn.getTypesAndValues()))
    .map(tv -> tv.getValue().toString())
    .findFirst()
    .orElse("")

extractSanEmails(PKCS10CertificationRequest csr):
  // Extract rfc822Name entries from SubjectAltName extension in CSR attributes
  // Return empty list if no SAN extension present
```

---

## 7. Service Specifications

### 7.1 `CsrValidationService.java`

**Package:** `com.pki.ra.adcsr.service`

```
@Service
Dependencies: HeaderValidator, Pkcs10CsrValidator, CsrIdentityMatcher

validate(AdIdentity identity, CsrSubmitRequest request) → CsrValidationResult:
  headerValidator.validate(identity)
  PKCS10CertificationRequest csr = pkcs10CsrValidator.validate(request.getPkcs10Pem())
  csrIdentityMatcher.match(identity, csr)
  return new CsrValidationResult(identity, csr, request.getCertificateProfile(),
                                 request.getValidityDays(), request.getRemarks())
```

---

### 7.2 `CsrSubmissionService.java`

**Package:** `com.pki.ra.adcsr.service`

```
@Service @Transactional
Dependencies: CsrValidationService, CsrSubmissionRepository, AuditLogService (from :common)

submit(AdIdentity identity, CsrSubmitRequest request, String sourceIp) → CsrSubmitResponse:

  Step 1 — Validate
    CsrValidationResult result = csrValidationService.validate(identity, request)

  Step 2 — Duplicate check
    String hash = sha256Hex(request.getPkcs10Pem().getBytes(UTF_8))
    if repository.existsByPkcs10Hash(hash):
      auditLogService.logFailure(identity.displayName(), "CSR_SUBMIT",
                                 null, "Duplicate CSR", sourceIp)
      throw new DuplicateCsrException("CSR already submitted")

  Step 3 — Build entity
    String submissionId = UUID.randomUUID().toString()
    String profile = result.certificateProfile() != null
                     ? result.certificateProfile() : "DSC"
    String sanitizedRemarks = HtmlPolicyBuilder.ALLOW_WITHOUT_TAGS.sanitize(request.getRemarks())

    CsrSubmissionRecord record = CsrSubmissionRecord.builder()
        .submissionId(submissionId)
        .adUsername(identity.username())
        .adEmail(identity.email())
        .pkcs10Hash(hash)
        .pkcs10Pem(request.getPkcs10Pem())
        .subjectCn(extractCN(result.parsedCsr()))
        .keyAlgorithm(resolveAlgorithm(result.parsedCsr()))
        .keySize(resolveKeySize(result.parsedCsr()))
        .ecCurve(resolveEcCurve(result.parsedCsr()))
        .certificateProfile(profile)
        .status("PENDING")
        .sourceIp(sourceIp)
        .remarks(sanitizedRemarks)
        .build()

  Step 4 — Persist
    repository.save(record)

  Step 5 — Audit
    auditLogService.logSuccess(identity.displayName(), "CSR_SUBMIT",
                               submissionId, "CSR submitted for profile " + profile, sourceIp)

  Step 6 — Return
    return CsrSubmitResponse.builder()
        .submissionId(submissionId)
        .status("PENDING")
        .submittedBy(identity.displayName())
        .submittedAt(Instant.now().toString())
        .message("CSR accepted and pending RA review")
        .build()
```

---

## 8. Repository Specification

### `CsrSubmissionRepository.java`

**Package:** `com.pki.ra.adcsr.repository`
**Extends:** `JpaRepository<CsrSubmissionRecord, Long>`

| Method | Return | Description |
|---|---|---|
| `existsByPkcs10Hash(String hash)` | `boolean` | Duplicate CSR check — called before save |
| `findBySubmissionId(String id)` | `Optional<CsrSubmissionRecord>` | Status query by caller |
| `findByAdUsername(String username, Pageable p)` | `Page<CsrSubmissionRecord>` | List by AD user |
| `findByStatus(String status, Pageable p)` | `Page<CsrSubmissionRecord>` | RA operator queue |
| `findByAdUsernameAndStatus(String username, String status, Pageable p)` | `Page<CsrSubmissionRecord>` | Filtered view |
| `countByAdUsernameAndCreatedAtAfter(String username, Instant since)` | `long` | Rate limit check |

---

## 9. Exception Specifications

### Exception Hierarchy

```
RuntimeException
  └── PkiRaException (from :common, has HttpStatus)
        ├── CsrValidationException       — 400 Bad Request
        ├── CsrIdentityMismatchException — 422 Unprocessable Entity
        ├── DuplicateCsrException        — 409 Conflict
        └── UnauthorizedProxyException   — 403 Forbidden
```

### `CsrValidationException.java`

```java
package com.pki.ra.adcsr.exception;

import com.pki.ra.common.exception.PkiRaException;
import org.springframework.http.HttpStatus;

public class CsrValidationException extends PkiRaException {
    public CsrValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
    public CsrValidationException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_REQUEST, cause);
    }
}
```

Same pattern for `CsrIdentityMismatchException` (422), `DuplicateCsrException` (409),
`UnauthorizedProxyException` (403).

---

### `GlobalExceptionHandler.java`

**Package:** `com.pki.ra.adcsr.exception`

```
@RestControllerAdvice

@ExceptionHandler(PkiRaException.class)
→ ProblemDetail.forStatusAndDetail(ex.getStatus(), htmlEscape(ex.getMessage()))

@ExceptionHandler(MethodArgumentNotValidException.class)
→ 400, field errors collected and returned as { "field": "error message" }

@ExceptionHandler(HttpMessageNotReadableException.class)
→ 400, "Request body is missing or malformed"

@ExceptionHandler(Exception.class)
→ 500, "An unexpected error occurred" (no internal detail exposed)
```

**Note:** `ex.getMessage()` is passed through `HtmlUtils.htmlEscape()` before
inclusion in the response body to prevent reflected XSS in error messages.

---

## 10. Security Config Specification

### `SecurityConfig.java`

**Package:** `com.pki.ra.adcsr.config`

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            // ── CSRF ────────────────────────────────────────────────────────
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            )
            // ── CORS ─────────────────────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // ── Security Headers ─────────────────────────────────────────────
            .headers(headers -> headers
                .contentSecurityPolicy(csp ->
                    csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .frameOptions(fo -> fo.deny())
                .contentTypeOptions(ct -> {})
                .httpStrictTransportSecurity(hsts ->
                    hsts.maxAgeInSeconds(31536000).includeSubDomains(true))
                .referrerPolicy(rp ->
                    rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .permissionsPolicy(pp ->
                    pp.policy("geolocation=(), microphone=(), camera=()"))
            )
            // ── Session ──────────────────────────────────────────────────────
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
            // ── Authorization ─────────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/ra/ad/csr/submit").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().denyAll()
            )
            // ── Custom Filter ─────────────────────────────────────────────────
            .addFilterBefore(adIdentityFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

---

## 11. Test Specification

### `CsrSubmitControllerTest.java`

| Test case | Expected |
|---|---|
| Valid request with username header | 202 Accepted |
| Valid request with email header | 202 Accepted |
| Missing both AD headers | 401 |
| Request from untrusted IP | 403 |
| Blank `pkcs10_pem` | 400 |
| Oversized CSR (> 8192 chars) | 400 |
| Invalid PEM format | 400 |
| CSR signature tampered | 400 |
| RSA 1024-bit key | 400 |
| CN does not match AD username | 422 |
| Duplicate CSR (same hash) | 409 |
| Missing CSRF token (browser flow) | 403 |

### `Pkcs10CsrValidatorTest.java`

| Test case | Expected |
|---|---|
| Valid RSA-2048 PEM CSR | Pass, returns parsed CSR |
| Valid ECDSA P-256 PEM CSR | Pass |
| Valid base64-DER CSR | Pass |
| CSR with tampered signature | `CsrValidationException` |
| RSA-1024 key | `CsrValidationException` |
| EC curve secp192r1 (not allowed) | `CsrValidationException` |
| No Subject CN | `CsrValidationException` |
| CSR body exceeds 16 KB | `CsrValidationException` |
| Completely random bytes | `CsrValidationException` |

### `CsrIdentityMatcherTest.java`

| Test case | Expected |
|---|---|
| CN = username, AD username present | Match |
| CN = email, AD email present | Match |
| SAN rfc822 = email, AD email present | Match |
| CN = random string, AD username present | `CsrIdentityMismatchException` |
| Case-insensitive CN match | Match |
