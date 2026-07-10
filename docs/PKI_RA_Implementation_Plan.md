# Enterprise PKI Registration Authority — Implementation Plan

## Context

The pki-ra project is a multi-module Spring Boot 4.0 / Java 25 application that currently has foundational infrastructure built (user management, RBAC, audit logging, error catalog, config caching, basic CA service). The goal is to develop it into a **full enterprise Registration Authority** comparable to EJBCA, Venafi, or Sectigo — capable of certificate lifecycle management, approval workflows, revocation, protocol support, and multi-CA connectivity.

The existing codebase follows strong patterns (Template Method, Provider, Specification, AbstractRefreshableCache) that should be reused throughout all phases.

---

## Phase 1: Certificate Lifecycle Core (XL) — CRITICAL FOUNDATION

**Goal:** End-to-end CSR submission → approval → issuance → storage → download

### New Tables
| Table | Purpose |
|-------|---------|
| `certificate_requests` | CSR blob, subject DN, requester, status (PENDING/APPROVED/REJECTED/ISSUED/FAILED), template_id FK |
| `certificates` | serial, subject, issuer, not_before, not_after, pem, status (ACTIVE/REVOKED/EXPIRED/SUSPENDED), request_id FK |
| `certificate_request_approvals` | request_id FK, approver, decision, comment, timestamp |

### New APIs
- `POST /api/ra/requests` — submit CSR
- `GET /api/ra/requests/{id}` — get request status
- `POST /api/ra/requests/{id}/approve` — approve (ROLE_OPERATOR)
- `POST /api/ra/requests/{id}/reject` — reject with reason
- `GET /api/ra/certificates/{serial}` — get cert details
- `GET /api/ra/certificates/{serial}/download?format=pem|der|p7b|p12` — download

### Reuse
- `CertificateIssuanceService` (caservice) — called after approval to sign CSR
- `AuditLogService` — log every request/approval/issuance
- `BaseAuditEntity` — extend for new entities
- `ErrorCodeKey` — add codes PKI_CERT_001 through PKI_CERT_010
- `AbstractGlobalExceptionHandler` — already handles all exception types

### Module Placement
- Entities → `common`
- Services + Controllers → `raservice`
- GUI pages → `gui`

---

## Phase 2: Certificate Templates & Policy Engine (L)

**Goal:** Predefined cert profiles enforcing key usage, validity, naming rules

### New Tables
| Table | Purpose |
|-------|---------|
| `certificate_templates` | name, key_usages, ext_key_usages, max_validity_days, min_key_size, allowed_algorithms, allowed_san_types, subject_dn_pattern, is_active |
| `template_permissions` | template_id FK, role_id FK (which roles can use which templates) |

### Template Types
- TLS Server, TLS Client, Code Signing, S/MIME Email, Document Signing

### APIs
- `GET/POST/PUT /api/ra/templates` — CRUD (ROLE_ADMIN)

### Reuse
- `AbstractRefreshableCache` → `TemplateCacheBean`
- `AbstractRefreshController` → hot-reload endpoint

---

## Phase 3: Revocation Management (L)

**Goal:** Full revocation lifecycle + CRL generation + OCSP responder

### New Tables
| Table | Purpose |
|-------|---------|
| `certificate_revocations` | certificate_id FK, reason_code (RFC 5280), revocation_time, revoked_by |
| `crl_entries` | crl_number, this_update, next_update, crl_blob, is_delta |

### APIs
- `POST /api/ra/certificates/{serial}/revoke` — revoke with reason
- `POST /api/ra/certificates/{serial}/suspend` — certificateHold
- `POST /api/ra/certificates/{serial}/unsuspend` — remove hold
- `GET /api/ra/crl/current` — download CRL
- `POST /api/ra/ocsp` — OCSP responder (RFC 6960)

### New Quartz Jobs
- `CrlGenerationJob` — scheduled CRL generation
- `ExpiredCertificateCleanupJob` — mark expired certs

### Reuse
- `CaProperties` (already has `crlDistributionPoint`, `ocspUrl` fields)
- BouncyCastle (already present for CRL/OCSP generation)

---

## Phase 4: Notification System & Expiry Tracking (M)

**Goal:** Proactive alerts — complete the stubbed `CertificateExpiryNotificationJob`

### New Tables
| Table | Purpose |
|-------|---------|
| `notifications` | user_id FK, type (EMAIL/WEBHOOK/IN_APP), event_type, certificate_id FK, status, sent_at |
| `notification_channels` | name, type, config_json (SMTP/webhook settings) |
| `notification_rules` | event_type, channel_id FK, days_before_expiry |

### Features
- Configurable thresholds (90, 60, 30, 14, 7, 1 days)
- Email via Spring Mail
- Webhook (POST to configured URLs on cert events)
- In-app notification inbox

### Reuse
- Existing `CertificateExpiryNotificationJob` stub in scheduler module
- `AppConfig` for notification settings

---

## Phase 5: Advanced Approval Workflows (L)

**Goal:** Multi-level approvals, auto-approve policies, delegation, escalation

### New Tables
| Table | Purpose |
|-------|---------|
| `approval_policies` | name, template_id FK, approval_chain_json, auto_approve_conditions, escalation_hours |
| `approval_delegations` | delegator_user_id, delegate_user_id, valid_from, valid_until |
| `approval_groups` | name, description + `approval_group_members` junction |

### New Quartz Job
- `ApprovalEscalationJob` — auto-escalate if not approved within N hours

---

## Phase 6: Reporting & Dashboard (M)

**Goal:** Operational visibility — cert inventory, expiry forecast, compliance reports

### APIs
- `GET /api/ra/dashboard/summary` — aggregate stats
- `GET /api/ra/dashboard/expiry-forecast` — upcoming expirations
- `GET /api/ra/reports/inventory?format=csv|pdf` — exportable inventory
- `GET /api/ra/reports/compliance` — compliance findings

### Reuse
- Existing `ActionSummaryProjection` for audit analytics — extend pattern for cert analytics

---

## Phase 7: Protocol Support — EST & SCEP (XL)

**Goal:** Industry-standard enrollment protocols for automated device provisioning

### New Tables
| Table | Purpose |
|-------|---------|
| `protocol_clients` | client_id, protocol (EST/SCEP), auth_type, shared_secret_hash, is_active |
| `protocol_transactions` | client_id FK, protocol, transaction_id, status, certificate_id FK |

### APIs (RFC-defined paths)
- EST: `/.well-known/est/simpleenroll`, `/simplereenroll`, `/cacerts`, `/csrattrs`
- SCEP: `/scep/pkiclient.exe?operation=GetCACert|PKIOperation`

### Module Placement
- New package in `caservice` or separate `estservice` module

---

## Phase 8: Multi-CA Backend Support (XL)

**Goal:** Connect to external CAs alongside built-in CA

### New Tables
| Table | Purpose |
|-------|---------|
| `ca_connectors` | name, type (BUILTIN/EJBCA/ADCS/VAULT/AWS), config_json (encrypted), priority, is_active |
| `ca_connector_health` | connector_id FK, last_check, status, response_time_ms |

### Strategy Pattern
```
CaConnector (interface)
├── BuiltinCaConnector (wraps existing CertificateIssuanceService)
├── EjbcaCaConnector (EJBCA REST API)
├── VaultPkiConnector (HashiCorp Vault PKI)
├── AwsAcmConnector (AWS ACM Private CA)
└── AdcsCaConnector (Microsoft AD CS)
```

---

## Phase 9: Key Management & HSM Integration (XL)

**Goal:** Key escrow, recovery (dual-control), HSM as optional pluggable layer

### New Tables
| Table | Purpose |
|-------|---------|
| `key_escrow` | certificate_id FK, encrypted_key_blob, encryption_key_id |
| `key_recovery_requests` | escrow_id FK, requester, status, approver_1, approver_2 |
| `hsm_slots` | name, pkcs11_library_path, slot_id, is_active |

### New Roles
- `ROLE_KEY_RECOVERY_AGENT` — dual-control approval for key recovery

### Architecture Decision: HSM is Optional & Pluggable

HSM integration is designed as an **optional pluggable layer** — NOT a mandatory dependency. The system works fully with software keystores (PKCS#12) and can be upgraded to hardware HSM without code changes.

**When HSM IS Mandatory (Regulatory):**
| Scenario | Why |
|----------|-----|
| PCI-DSS (payment) | Private keys must be in hardware — audit fails without HSM |
| eIDAS (EU digital signatures) | Qualified certificates legally require HSM |
| FIPS 140-2 Level 3+ (gov/defense) | Tamper-evident hardware mandated |
| Public CA (TLS to public) | CA/Browser Forum Baseline Requirements |
| Banking / Financial services | RBI / regulatory guidelines |

**When HSM is NOT Required:**
| Scenario | Why |
|----------|-----|
| Internal enterprise PKI | No external regulatory mandate |
| Dev / Test / POC | PKCS#12 file works perfectly |
| mTLS between microservices | Internal trust — HSM is overkill |
| Small org (<500 certs) | Cost vs risk doesn't justify |

**Pluggable Architecture (Strategy Pattern):**

```
CertificateIssuanceService
         |
         | uses
         v
KeyStoreProvider (interface)
├── SoftwareKeyStoreProvider   ← Current (PKCS#12, works out of box)
│     Phase 1-8, zero config
└── HsmKeyStoreProvider        ← Phase 9 (PKCS#11, enable when needed)
      SafeNet Luna, Thales nShield, AWS CloudHSM, SoftHSM2 (dev)
```

**Configuration — Switch is One Property:**

```yaml
# Default — software (no HSM needed)
ca.keystore.provider: software
ca.keystore.path: ca-keystore.p12

# HSM — just change provider
ca.keystore.provider: hsm
ca.keystore.pkcs11.library: /opt/safenet/lib/libCryptoki2_64.so
ca.keystore.pkcs11.slot: 0
ca.keystore.pkcs11.pin: ${HSM_PIN}
```

**Cost:**
| Option | Cost | Use Case |
|--------|------|----------|
| PKCS#12 (software) | Free | Internal PKI, dev, test |
| SoftHSM2 | Free (open source) | Dev/test HSM simulation |
| AWS CloudHSM | ~$1.50/hr | Cloud-native production |
| SafeNet Luna | $15K-$50K | On-prem enterprise |
| Thales nShield | $20K-$80K | High-security / government |

---

## Phase 10: LDAP/AD Certificate Publishing (M)

**Goal:** Auto-publish certs to AD `userCertificate` attribute

### New Tables
| Table | Purpose |
|-------|---------|
| `ldap_publish_targets` | name, ldap_url, base_dn, bind_dn, bind_password_encrypted, is_active |
| `ldap_publish_log` | certificate_id FK, target_id FK, status, published_at |

### Reuse
- Existing AD/LDAP auth config from `gui` module (reuse connection factory)

---

## Phase 11: CMP & ACME Protocol Support (XL)

**Goal:** RFC 4210 CMP + RFC 8555 ACME for advanced automation

### New Tables
- `acme_accounts`, `acme_orders`, `acme_authorizations`
- CMP transaction tracking

---

## Phase 12: Compliance, CT & SIEM Integration (L)

**Goal:** Policy enforcement, Certificate Transparency, security event forwarding

### New Tables
| Table | Purpose |
|-------|---------|
| `compliance_policies` | name, type, rule_json, severity |
| `compliance_violations` | certificate_id FK, policy_id FK, detail |
| `ct_submissions` | certificate_id FK, log_url, sct |
| `siem_endpoints` | name, type (SYSLOG/CEF/WEBHOOK), config_json |

---

## Cross-Cutting Concerns (All Phases)

| Concern | Approach |
|---------|----------|
| **Error codes** | Each phase adds 5-15 codes to per-module `ErrorCodeKey` enum + Flyway seed |
| **Audit logging** | Every state change via existing `AuditLogService` |
| **Flyway migrations** | One migration file per table per phase |
| **Caching** | Reference data uses `AbstractRefreshableCache` with hot-reload |
| **API docs** | `@Operation`, `@ApiResponse` per existing pattern |
| **Security** | Existing RBAC; new roles as needed: `ROLE_RA_OFFICER`, `ROLE_KEY_RECOVERY_AGENT` |
| **Testing** | Unit + integration tests per service; H2 profile for dev seeding |

---

## Priority Summary

| Phase | Name | Size | Priority |
|-------|------|------|----------|
| 1 | Certificate Lifecycle Core | XL | **CRITICAL** — everything depends on this |
| 2 | Certificate Templates | L | **HIGH** — standardization |
| 3 | Revocation Management | L | **HIGH** — security requirement |
| 4 | Notifications & Expiry | M | **HIGH** — operational necessity |
| 5 | Advanced Workflows | L | MEDIUM — enterprise requirement |
| 6 | Reporting & Dashboard | M | MEDIUM — visibility |
| 7 | EST & SCEP | XL | MEDIUM — device automation |
| 8 | Multi-CA Support | XL | MEDIUM — enterprise flexibility |
| 9 | Key Management & HSM | XL | MEDIUM — compliance |
| 10 | LDAP Publishing | M | MEDIUM — AD integration |
| 11 | CMP & ACME | XL | LOWER — advanced automation |
| 12 | Compliance & CT & SIEM | L | LOWER — mature-stage |

---

## Verification Plan

After each phase:
1. **Build:** `gradle clean build` — all modules compile
2. **Tests:** Unit tests for services, integration tests with H2
3. **Startup Validation:** `ErrorCatalogStartupValidator` verifies all new error codes exist
4. **API Test:** OpenAPI/Swagger UI at `/swagger-ui.html` — test all new endpoints
5. **Audit Check:** Verify every operation creates audit log entries
6. **Security Check:** Verify unauthorized users get 403 on protected endpoints
7. **Cache Check:** POST `/refresh` reloads new cached data without restart

---

## Starting Point

**Begin with Phase 1** — Certificate Lifecycle Core. This creates the `certificate_requests` and `certificates` tables and the submission → approval → issuance flow. Every subsequent phase builds on these tables.
