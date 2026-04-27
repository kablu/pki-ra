# Baseline Requirements Specification
# Active Directory Authentication — PKI-RA System

---

| Field | Detail |
|---|---|
| **Document ID** | PKI-RA-BRS-001 |
| **Document Title** | Baseline Requirements Specification — AD Authentication |
| **Project** | PKI-RA (Public Key Infrastructure — Registration Authority) |
| **Version** | 1.0 |
| **Status** | Baseline |
| **Prepared By** | PKI-RA Project Team |
| **Review Date** | 2026-04-27 |
| **Classification** | Internal — Restricted |

---

## Revision History

| Version | Date | Author | Description |
|---|---|---|---|
| 0.1 | 2026-04-20 | PKI-RA Team | Initial draft |
| 0.2 | 2026-04-24 | PKI-RA Team | Stakeholder review comments incorporated |
| 1.0 | 2026-04-27 | PKI-RA Team | Baselined after sign-off |

---

## Approvals

| Role | Name | Signature | Date |
|---|---|---|---|
| Project Sponsor | | | |
| IT Security Officer | | | |
| Enterprise Architect | | | |
| PKI Operations Lead | | | |
| CISO Representative | | | |

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Purpose & Scope](#2-purpose--scope)
3. [Stakeholders](#3-stakeholders)
4. [Definitions, Acronyms & Abbreviations](#4-definitions-acronyms--abbreviations)
5. [Assumptions & Dependencies](#5-assumptions--dependencies)
6. [Constraints](#6-constraints)
7. [Business Requirements](#7-business-requirements)
8. [Stakeholder Requirements](#8-stakeholder-requirements)
9. [Functional Requirements](#9-functional-requirements)
10. [Non-Functional Requirements](#10-non-functional-requirements)
11. [Security Requirements](#11-security-requirements)
12. [Integration Requirements](#12-integration-requirements)
13. [Compliance & Regulatory Requirements](#13-compliance--regulatory-requirements)
14. [User Interface Requirements](#14-user-interface-requirements)
15. [Data Requirements](#15-data-requirements)
16. [Operational Requirements](#16-operational-requirements)
17. [Transition Requirements](#17-transition-requirements)
18. [Acceptance Criteria](#18-acceptance-criteria)
19. [Out of Scope](#19-out-of-scope)
20. [Open Issues & Risks](#20-open-issues--risks)
21. [Glossary](#21-glossary)

---

## 1. Introduction

### 1.1 Background

The PKI-RA (Public Key Infrastructure — Registration Authority) system is responsible for managing the full lifecycle of digital certificates, including user registration, identity verification, certificate issuance, approval workflows, and revocation. It acts as the trusted intermediary between end entities (applicants) and the Certificate Authority (CA).

Currently, the system requires a dedicated authentication mechanism for internal users — RA Operators, RA Administrators, and Auditors — who access the system to process certificate requests, manage workflows, and perform oversight functions.

The organisation's IT infrastructure operates on Microsoft Active Directory (AD) as the central identity and access management platform for all internal staff. Requiring separate credentials for the PKI-RA system creates operational overhead, increases security risk through credential sprawl, and creates friction for authorised users.

### 1.2 Problem Statement

The PKI-RA system currently lacks integration with the organisation's centralised Active Directory infrastructure. This results in:

- Separate credential management for PKI-RA users, outside the corporate password policy.
- Inability to enforce Multi-Factor Authentication (MFA) consistently.
- Manual provisioning and de-provisioning of PKI-RA accounts, creating risk of orphaned accounts.
- No automatic enforcement of role changes when a user's position or AD group membership changes.
- Delayed access revocation when staff leave or change roles.
- Audit trails are fragmented between AD logs and PKI-RA application logs.

### 1.3 Opportunity

Integrating Active Directory authentication into the PKI-RA system will:

- Enable single-sign-on (SSO) using corporate credentials.
- Automatically inherit the organisation's password policy, lockout policy, and MFA requirements.
- Enable role assignment to be governed by existing AD group membership.
- Ensure immediate access revocation when AD accounts are disabled.
- Consolidate identity governance within the existing IAM framework.

---

## 2. Purpose & Scope

### 2.1 Purpose

This Baseline Requirements Specification (BRS) defines the complete, agreed, and baselined set of requirements for the Active Directory Authentication component of the PKI-RA system. It serves as the contractual reference for design, development, testing, and acceptance of this component.

### 2.2 In-Scope

| # | Item |
|---|---|
| S-01 | Authentication of internal PKI-RA users (RA Operators, RA Admins, Auditors) via Active Directory |
| S-02 | LDAP/LDAPS integration with the corporate Active Directory domain controller(s) |
| S-03 | Mapping of AD security groups to PKI-RA application roles (RBAC) |
| S-04 | Session management for authenticated users |
| S-05 | Audit logging of all authentication events (success, failure, logout, lockout) |
| S-06 | Brute-force and account lockout protection |
| S-07 | Support for multiple Active Directory domain controllers (primary + failover) |
| S-08 | Just-In-Time (JIT) user provisioning from AD attributes on first login |
| S-09 | Secure credential transport using LDAPS (SSL/TLS) |
| S-10 | Configuration of AD connection parameters via administration interface |

### 2.3 Out of Scope

Refer to Section 19 for the full out-of-scope register.

---

## 3. Stakeholders

### 3.1 Stakeholder Register

| ID | Stakeholder | Role | Interest | Influence |
|---|---|---|---|---|
| SH-01 | PKI Operations Lead | Process Owner | Ensure operational continuity | High |
| SH-02 | IT Security Officer | Security Approver | Enforce security standards | High |
| SH-03 | RA Operators | End Users | Seamless login with corporate credentials | Medium |
| SH-04 | RA Administrators | End Users / Config | Manage AD mappings and audit | High |
| SH-05 | IT Infrastructure Team | AD Owners | Provide AD service account, group policy | High |
| SH-06 | Compliance & Audit Team | Oversight | Audit trails and regulatory compliance | High |
| SH-07 | CISO / Security Governance | Sponsor | Security policy enforcement | High |
| SH-08 | Enterprise Architect | Design Authority | Architecture alignment | Medium |
| SH-09 | HR / Identity Governance | Joiner-Mover-Leaver | Prompt deprovisioning | Medium |
| SH-10 | Help Desk | Support | Handling login and lockout issues | Low |

### 3.2 RACI Matrix

| Activity | PKI Ops | IT Security | IT Infrastructure | Compliance | Dev Team |
|---|---|---|---|---|---|
| Requirements sign-off | A | C | C | C | R |
| AD service account provisioning | I | C | R/A | — | — |
| AD group creation (PKI roles) | C | A | R | — | — |
| System design | C | A | C | — | R |
| Development & unit testing | — | — | — | — | R/A |
| Security testing | I | A | — | C | R |
| UAT | R/A | C | — | C | S |
| Go-live approval | C | A | C | C | R |

*R = Responsible, A = Accountable, C = Consulted, I = Informed, S = Support*

---

## 4. Definitions, Acronyms & Abbreviations

| Term | Definition |
|---|---|
| **AD** | Active Directory — Microsoft's directory service for identity management |
| **BRS** | Baseline Requirements Specification |
| **CA** | Certificate Authority — issues digital certificates |
| **DC** | Domain Controller — server hosting Active Directory |
| **DN** | Distinguished Name — unique LDAP identifier for an object |
| **FQDN** | Fully Qualified Domain Name |
| **JIT** | Just-In-Time — automatic user provisioning on first login |
| **JWT** | JSON Web Token — compact, signed token for stateless auth |
| **KPI** | Key Performance Indicator |
| **LDAP** | Lightweight Directory Access Protocol (port 389, unencrypted) |
| **LDAPS** | LDAP over SSL/TLS (port 636, encrypted) |
| **MFA** | Multi-Factor Authentication |
| **MR** | Merge Request (version control term) |
| **PKI** | Public Key Infrastructure |
| **PKI-RA** | PKI Registration Authority |
| **RA** | Registration Authority |
| **RBAC** | Role-Based Access Control |
| **SAMAccountName** | Security Account Manager Account Name — legacy AD logon identifier |
| **SLA** | Service Level Agreement |
| **SSO** | Single Sign-On |
| **TLS** | Transport Layer Security |
| **UPN** | User Principal Name — modern AD logon format (user@domain.com) |
| **LDAP Bind** | Authentication operation in LDAP — verifying credentials |
| **Service Account** | Dedicated AD account used by the application to search the directory |
| **objectGUID** | Unique, immutable identifier for an AD object |

---

## 5. Assumptions & Dependencies

### 5.1 Assumptions

| ID | Assumption |
|---|---|
| A-01 | The organisation operates a Microsoft Active Directory domain accessible from the PKI-RA server network segment. |
| A-02 | All PKI-RA internal users (RA Operators, RA Admins, Auditors) have active AD accounts within the corporate domain. |
| A-03 | The IT Infrastructure team will provision a dedicated AD service account for the PKI-RA application with read-only directory search permissions. |
| A-04 | AD security groups will be created or designated specifically for PKI-RA role assignment (e.g. PKI_RA_Admins, PKI_RA_Operators, PKI_RA_Auditors). |
| A-05 | The network allows LDAPS (TCP 636) traffic from the PKI-RA application server to the domain controllers. |
| A-06 | The AD domain controller SSL certificate is issued by a CA trusted by the PKI-RA server's JVM trust store. |
| A-07 | The organisation's existing password and lockout policies in AD are considered sufficient and will be inherited. |
| A-08 | Users will access the PKI-RA system via the corporate internal network or VPN. |
| A-09 | At least two domain controllers are available (one primary, one failover) for high availability. |
| A-10 | The secrets management solution (e.g. HashiCorp Vault) is available for storing the AD service account bind password. |

### 5.2 Dependencies

| ID | Dependency | Owner | Impact if Not Met |
|---|---|---|---|
| D-01 | AD service account provisioned with appropriate permissions | IT Infrastructure | Authentication cannot be configured |
| D-02 | AD security groups for PKI-RA roles created and populated | IT Infrastructure / PKI Ops | Role mapping cannot be tested or activated |
| D-03 | LDAPS firewall rules opened (TCP 636) | Network / IT Infra | Secure connectivity cannot be established |
| D-04 | DC SSL certificate trusted by application server | IT Infrastructure | TLS handshake fails; LDAPS unavailable |
| D-05 | Secrets management platform available | Platform / DevOps | Bind password cannot be securely stored |
| D-06 | PKI-RA database schema deployed (ad_configurations, ad_user_mappings, etc.) | Dev Team | Mapping and session persistence unavailable |

---

## 6. Constraints

| ID | Type | Constraint |
|---|---|---|
| C-01 | Technical | The system MUST use LDAPS (port 636, TLS 1.2 minimum). Plain LDAP (port 389) is prohibited in production. |
| C-02 | Technical | The AD service account bind password MUST NOT be stored in configuration files or source code. It MUST be resolved at runtime from an approved secrets management solution. |
| C-03 | Technical | The PKI-RA system must be built on Java 17+ / Spring Boot 3.x using Spring Security's LDAP integration. |
| C-04 | Security | Private keys MUST NOT be stored in the authentication database. |
| C-05 | Security | Authentication tokens (JWT / session IDs) MUST NOT be stored in plain text. Only SHA-256 hashes are stored. |
| C-06 | Operational | AD group membership is authoritative for role assignment. Local role overrides are not permitted without RA Admin approval. |
| C-07 | Compliance | All authentication events MUST be logged in the audit trail with sufficient detail to support forensic investigation. |
| C-08 | Performance | Authentication response time MUST NOT exceed 3 seconds at the 95th percentile under normal load. |
| C-09 | Architecture | The system MUST support a minimum of two AD domain controllers to avoid a single point of failure. |
| C-10 | Data | User personal data fetched from AD (display name, email, department) MUST be handled in accordance with the organisation's data protection policy. |

---

## 7. Business Requirements

### 7.1 Business Goals

| ID | Business Requirement | Priority |
|---|---|---|
| BR-01 | The PKI-RA system SHALL authenticate all internal users (RA Operators, RA Admins, Auditors) exclusively via the corporate Active Directory. | Must Have |
| BR-02 | The system SHALL eliminate the need for PKI-RA-specific user credentials, removing the operational overhead of managing a separate user store. | Must Have |
| BR-03 | When a user's AD account is disabled, their PKI-RA access SHALL be revoked within one business day without manual intervention in the PKI-RA system. | Must Have |
| BR-04 | The system SHALL enforce the organisation's existing password policy and MFA requirements automatically, without additional configuration in PKI-RA. | Must Have |
| BR-05 | Role assignments SHALL be controlled by Active Directory group membership, enabling the Identity Governance team to manage PKI-RA access via their existing IAM tooling. | Must Have |
| BR-06 | The system SHALL provide a consolidated audit trail for all PKI-RA authentication events, accessible to the Compliance and Audit team. | Must Have |
| BR-07 | The system SHALL remain operational when a single AD domain controller is unavailable, without manual failover steps. | Must Have |
| BR-08 | The system SHALL automatically provision a PKI-RA user profile on first successful login, without requiring an administrator to pre-create the account. | Should Have |
| BR-09 | RA Administrators SHALL be able to configure AD connection parameters (domain controllers, base DNs, group mappings) via the administration interface without redeployment. | Should Have |
| BR-10 | The system SHALL produce metric data enabling measurement of authentication performance against the defined KPIs. | Should Have |

### 7.2 Business KPIs

| KPI | Target | Measurement |
|---|---|---|
| Orphaned account elimination | 0 active PKI-RA sessions for disabled AD accounts within 1 business day | Automated scan of session table vs AD |
| Authentication success rate | ≥ 99.5% during business hours (excluding genuine lockouts) | Application metrics dashboard |
| Mean time to access provisioning | ≤ 5 minutes from AD group assignment to first PKI-RA login | Timestamp: group added → first successful login |
| Authentication latency (P95) | ≤ 3 seconds | Application performance monitoring |
| Failed login incident response | Alert raised within 5 minutes of threshold breach (≥ 5 failures in 10 minutes per account) | SIEM integration |

---

## 8. Stakeholder Requirements

### 8.1 RA Operators (SH-03)

| ID | Requirement |
|---|---|
| STK-01 | I want to log in to PKI-RA using my existing corporate domain credentials (username/password), so that I do not need to remember separate credentials. |
| STK-02 | I want Single Sign-On behaviour if the organisation deploys a corporate SSO solution in future, so that I can access PKI-RA without re-entering credentials. |
| STK-03 | I want clear, descriptive error messages when my login fails (e.g. "Account locked", "Invalid credentials", "Account not authorised for PKI-RA"), so that I know what action to take. |
| STK-04 | I want my session to remain active for a configurable duration during active work, so that I am not repeatedly prompted to re-authenticate during a single work session. |

### 8.2 RA Administrators (SH-04)

| ID | Requirement |
|---|---|
| STK-05 | I want to view and manage the mapping between Active Directory groups and PKI-RA roles, so that I can control who has which level of access. |
| STK-06 | I want to be able to force-expire a user's active session from the administration console, so that I can respond to security incidents immediately. |
| STK-07 | I want to configure the AD domain controller addresses, base DNs, and service account credentials without redeployment, so that I can respond to infrastructure changes quickly. |
| STK-08 | I want to test the AD connection from the administration console, so that I can validate configuration changes before they affect users. |
| STK-09 | I want to view a log of all authentication events (success, failure, lockout, logout) filterable by user, date, and event type. |

### 8.3 IT Security Officer (SH-02)

| ID | Requirement |
|---|---|
| STK-10 | I require that all credentials are transmitted exclusively over LDAPS (TLS), so that credentials cannot be intercepted in transit. |
| STK-11 | I require that the AD service account bind password is stored in an approved secrets management solution, not in configuration files. |
| STK-12 | I require that brute-force login attempts trigger automatic account lockout (governed by AD policy) and generate a security alert. |
| STK-13 | I require that session tokens are stored as cryptographic hashes only, never in plain text, to limit the impact of a database compromise. |
| STK-14 | I require that access rights are re-evaluated at each login based on current AD group membership, not cached indefinitely. |

### 8.4 Compliance & Audit Team (SH-06)

| ID | Requirement |
|---|---|
| STK-15 | I require that every authentication event is logged with: timestamp (UTC), user identity (UPN + SAMAccountName), source IP, event type, and outcome. |
| STK-16 | I require that audit logs are immutable — no application-level delete or update operations are permitted on authentication audit records. |
| STK-17 | I require that audit logs are retained for a minimum of 3 years in accordance with the organisation's retention policy. |
| STK-18 | I require that authentication audit logs are exportable in a machine-readable format (CSV/JSON) for regulatory reporting. |

### 8.5 IT Infrastructure Team (SH-05)

| ID | Requirement |
|---|---|
| STK-19 | I want the PKI-RA application to use a single, dedicated read-only service account for LDAP bind operations, with the least-privilege permissions required. |
| STK-20 | I want the application to support multiple domain controllers (primary + failover) so that a single DC maintenance window does not cause PKI-RA downtime. |
| STK-21 | I want to receive an alert if the PKI-RA application cannot connect to any configured domain controller. |

---

## 9. Functional Requirements

### 9.1 Authentication

| ID | Requirement | Priority | Notes |
|---|---|---|---|
| FR-AUTH-001 | The system SHALL authenticate users by performing an LDAP bind operation against the corporate Active Directory domain using the user's SAMAccountName or UPN and password. | Must Have | |
| FR-AUTH-002 | The system SHALL perform the LDAP bind exclusively over LDAPS (port 636, TLS 1.2 minimum). | Must Have | Plain LDAP (port 389) prohibited |
| FR-AUTH-003 | The system SHALL first perform a directory search using the service account to locate the user's distinguished name (DN) before attempting the user bind. | Must Have | Supports UPN and SAMAccountName lookups |
| FR-AUTH-004 | The system SHALL reject authentication immediately if the user's AD account is disabled, expired, or locked, and return an appropriate error message to the user. | Must Have | |
| FR-AUTH-005 | The system SHALL support both SAMAccountName (DOMAIN\username) and UPN (user@domain.com) login formats. | Must Have | |
| FR-AUTH-006 | The system SHALL validate that the authenticated user is a member of at least one PKI-RA authorised AD security group. Users not in any PKI-RA group SHALL be denied access with an "Unauthorised" error. | Must Have | |
| FR-AUTH-007 | The system SHALL support a configurable primary domain controller and a minimum of one failover domain controller. If the primary is unreachable, the system SHALL automatically retry against the failover. | Must Have | |
| FR-AUTH-008 | The system SHALL propagate specific AD sub-error codes to application-level exceptions, enabling the UI to display context-specific error messages (e.g. account locked, password expired). | Should Have | |
| FR-AUTH-009 | The system SHALL impose an application-level rate limit of no more than 5 failed login attempts within 10 minutes per username, blocking further attempts and raising an alert regardless of AD lockout policy. | Must Have | Defence-in-depth; not a replacement for AD lockout |

### 9.2 Role Mapping

| ID | Requirement | Priority | Notes |
|---|---|---|---|
| FR-ROLE-001 | The system SHALL map Active Directory security groups to PKI-RA application roles (RA_OPERATOR, RA_ADMIN, AUDITOR, RA_VIEWER) via a configurable mapping table. | Must Have | |
| FR-ROLE-002 | The AD-to-role mapping SHALL be configurable by RA Administrators without redeployment. | Must Have | |
| FR-ROLE-003 | At login, the system SHALL read the user's current AD group memberships and derive their PKI-RA roles. The derived roles SHALL be stored in the session/token. | Must Have | |
| FR-ROLE-004 | If a user belongs to multiple mapped AD groups, all corresponding PKI-RA roles SHALL be granted (additive role model). | Must Have | |
| FR-ROLE-005 | AD group membership SHALL be re-evaluated at each login. Cached role assignments SHALL not persist beyond the current session. | Must Have | |
| FR-ROLE-006 | If an AD group mapping is removed or a user is removed from an AD group, the change SHALL take effect at the user's next login. Active sessions are not immediately revoked; session termination handles this. | Must Have | Immediate revocation is out of scope |

### 9.3 Session Management

| ID | Requirement | Priority | Notes |
|---|---|---|---|
| FR-SESS-001 | Upon successful authentication, the system SHALL issue a signed JWT (JSON Web Token) containing: sub (userId), upn, roles, jti (unique token ID), iat, exp claims. | Must Have | |
| FR-SESS-002 | The system SHALL record each active session in the `ad_auth_sessions` table, storing only the SHA-256 hash of the JWT — never the token itself. | Must Have | |
| FR-SESS-003 | The JWT access token expiry SHALL be configurable (default: 30 minutes). A refresh mechanism SHALL allow extension without full re-authentication, up to a configurable maximum session duration (default: 8 hours). | Should Have | |
| FR-SESS-004 | The system SHALL enforce a configurable maximum of concurrent sessions per user (default: 1). Logging in when at the limit SHALL either reject the new login or invalidate the oldest session, based on configuration. | Should Have | |
| FR-SESS-005 | The system SHALL support explicit logout, which SHALL invalidate the server-side session record and add the JWT jti to a blocklist. | Must Have | |
| FR-SESS-006 | The system SHALL automatically expire sessions that have been inactive beyond the configured idle timeout (default: 30 minutes). | Must Have | |
| FR-SESS-007 | RA Administrators SHALL be able to force-terminate any active session via the administration interface. | Must Have | |

### 9.4 Just-In-Time (JIT) User Provisioning

| ID | Requirement | Priority | Notes |
|---|---|---|---|
| FR-JIT-001 | On a user's first successful authentication, the system SHALL automatically create a PKI-RA user profile using attributes fetched from AD: displayName, mail, department, title, SAMAccountName, UPN, objectGUID. | Must Have | |
| FR-JIT-002 | The AD `objectGUID` SHALL be stored as the stable AD identity anchor for the user record, enabling the record to survive UPN or SAMAccountName changes. | Must Have | |
| FR-JIT-003 | On subsequent logins, the system SHALL update the user's PKI-RA profile with any changed AD attributes (displayName, email, department). | Should Have | |
| FR-JIT-004 | JIT provisioning SHALL be transactional — if profile creation fails, the login SHALL fail cleanly and the failure SHALL be logged. | Must Have | |

### 9.5 Audit Logging

| ID | Requirement | Priority | Notes |
|---|---|---|---|
| FR-AUDIT-001 | The system SHALL log every authentication attempt with: timestamp (UTC), username (SAMAccountName + UPN where available), source IP address, event type, outcome (SUCCESS / FAILURE), and failure reason code. | Must Have | |
| FR-AUDIT-002 | The system SHALL log session events: session created, session expired (idle), session expired (absolute), session force-terminated, logout. | Must Have | |
| FR-AUDIT-003 | The system SHALL log AD configuration changes: DC address updates, group mapping changes, service account changes. | Must Have | |
| FR-AUDIT-004 | Authentication audit records SHALL be insert-only. The application layer SHALL NOT provide any update or delete operations on audit records. | Must Have | |
| FR-AUDIT-005 | Audit logs SHALL be partitioned by month to support efficient querying and retention management. | Should Have | |

### 9.6 Administration Interface

| ID | Requirement | Priority | Notes |
|---|---|---|---|
| FR-ADMIN-001 | The system SHALL provide an administration screen (accessible to RA_ADMIN role only) to configure: primary and failover DC hostnames, LDAP base DN, search filter, service account credentials (masked), connection timeout, LDAPS enabled/disabled flag. | Must Have | |
| FR-ADMIN-002 | The administration interface SHALL provide a "Test Connection" function that performs a live LDAP bind using the configured service account and reports success or failure with diagnostic details. | Must Have | |
| FR-ADMIN-003 | The administration interface SHALL display the current AD-to-role group mappings and allow RA Admins to add, edit, or deactivate mappings. | Must Have | |
| FR-ADMIN-004 | The administration interface SHALL display all active sessions with: username, login time, last active time, source IP, expiry time, and a "Force Terminate" action. | Must Have | |
| FR-ADMIN-005 | All changes made through the administration interface SHALL be recorded in the audit log with the acting administrator's identity. | Must Have | |

---

## 10. Non-Functional Requirements

### 10.1 Performance

| ID | Requirement | Target |
|---|---|---|
| NFR-PERF-001 | End-to-end login response time (from credential submission to JWT issued) SHALL NOT exceed 3 seconds at the 95th percentile under normal operational load. | P95 ≤ 3 s |
| NFR-PERF-002 | LDAP bind and directory search operations SHALL each complete within 1 second at the 95th percentile under normal load. | P95 ≤ 1 s |
| NFR-PERF-003 | The authentication subsystem SHALL support a minimum of 50 concurrent login requests without degradation beyond the P95 target. | 50 concurrent |
| NFR-PERF-004 | Session validation (JWT verification + session record lookup) SHALL complete within 50 ms at the 99th percentile. | P99 ≤ 50 ms |

### 10.2 Availability & Reliability

| ID | Requirement | Target |
|---|---|---|
| NFR-AVAIL-001 | The AD authentication service SHALL achieve ≥ 99.9% availability during business hours (07:00–20:00 local time, Mon–Fri). | ≥ 99.9% |
| NFR-AVAIL-002 | Loss of the primary domain controller SHALL NOT cause authentication downtime. Failover to a secondary DC SHALL occur transparently within 10 seconds. | ≤ 10 s failover |
| NFR-AVAIL-003 | Transient LDAP connection errors SHALL trigger a configurable retry (default: 3 attempts, 500 ms back-off) before surfacing an error to the user. | 3 retries |
| NFR-AVAIL-004 | A health check endpoint SHALL be provided that verifies LDAP connectivity to at least one DC and returns HTTP 200 (healthy) or HTTP 503 (degraded). | — |

### 10.3 Scalability

| ID | Requirement |
|---|---|
| NFR-SCALE-001 | The authentication module SHALL be stateless at the application tier (all session state persisted in the database), enabling horizontal scaling of the application without session stickiness. |
| NFR-SCALE-002 | The session and audit tables SHALL be designed (partitioned, indexed) to sustain ≥ 5 years of data without query performance degradation beyond the stated NFRs. |

### 10.4 Maintainability

| ID | Requirement |
|---|---|
| NFR-MAINT-001 | All configurable parameters (DC hosts, timeouts, token expiry, rate limits) SHALL be externalised to the application configuration, not hard-coded. |
| NFR-MAINT-002 | The AD authentication module SHALL be independently deployable as a Spring Boot autoconfiguration module or clearly bounded package, minimising coupling to other PKI-RA modules. |
| NFR-MAINT-003 | The system SHALL support AD configuration changes without application restart where technically feasible. |

### 10.5 Testability

| ID | Requirement |
|---|---|
| NFR-TEST-001 | The authentication module SHALL be testable against an embedded LDAP directory (e.g. UnboundID LDAP SDK) in unit and integration test environments, without requiring a live AD connection. |
| NFR-TEST-002 | A dedicated non-production AD organisational unit or test domain SHALL be available for integration and UAT testing. |

---

## 11. Security Requirements

| ID | Requirement | Rationale |
|---|---|---|
| SR-001 | All LDAP communication SHALL use LDAPS (TLS 1.2 minimum; TLS 1.3 preferred). Plain LDAP (port 389) SHALL be disabled in all environments including development. | Credential confidentiality in transit |
| SR-002 | The AD service account bind password SHALL be retrieved at runtime from an approved secrets management solution (e.g. HashiCorp Vault). It SHALL NOT appear in any configuration file, environment variable file, source code, or build artifact. | Prevent credential leakage in repos and logs |
| SR-003 | Submitted user passwords SHALL NOT be stored, logged, or persisted at any point in the authentication flow. Only the outcome of the LDAP bind operation is recorded. | Prevent plaintext password storage |
| SR-004 | Session tokens (JWT) SHALL NOT be stored in the database. Only the SHA-256 hash of the JWT (or the jti claim value) SHALL be persisted for session tracking. | Limit impact of database breach |
| SR-005 | JWT tokens SHALL be signed using HMAC-SHA-512 or RS256. The signing secret/key SHALL be stored in the secrets management solution, not in application configuration. | Token integrity |
| SR-006 | All JWT claims SHALL be validated on every request: signature, expiry (exp), issued-at (iat), and jti (against blocklist). | Prevent replay attacks |
| SR-007 | The application SHALL enforce an application-level rate limit (≥ 5 failures / 10 minutes per username / IP) independently of AD lockout policy, as a defence-in-depth control. | Brute-force protection |
| SR-008 | LDAP search queries SHALL use parameterised filters. User-supplied input SHALL be escaped before inclusion in any LDAP filter string to prevent LDAP injection attacks. | LDAP injection prevention |
| SR-009 | AD attributes returned by directory searches (displayName, mail, department) SHALL be treated as untrusted input and sanitised before storage or display. | XSS / injection prevention |
| SR-010 | The LDAP connection pool SHALL use a dedicated read-only service account. The service account SHALL have permissions ONLY to: read user attributes and group memberships. Write access is not required and SHALL NOT be granted. | Least-privilege principle |
| SR-011 | All PKI-RA administration endpoints (AD config, group mappings, session management) SHALL require the RA_ADMIN role AND enforce re-authentication for sensitive operations (e.g. changing DC credentials). | Privilege escalation prevention |
| SR-012 | Security-relevant events (login failure bursts, configuration changes, force-terminated sessions) SHALL generate alerts to the SIEM / security monitoring platform. | Security monitoring |
| SR-013 | The application's SSL/TLS trust store SHALL contain only the specific CA certificate(s) that issued the domain controller certificates. System-wide trust stores SHALL NOT be used for this connection. | Certificate pinning / trust anchoring |
| SR-014 | Session cookies (if used for web UI) SHALL be marked HttpOnly, Secure, and SameSite=Strict. | Cookie security |
| SR-015 | The system SHALL implement CSRF protection for all state-changing HTTP requests in the web UI. | CSRF prevention |

---

## 12. Integration Requirements

### 12.1 Active Directory / LDAP

| ID | Requirement |
|---|---|
| IR-AD-001 | The system SHALL integrate with Microsoft Active Directory using LDAP v3 protocol over LDAPS (port 636). |
| IR-AD-002 | The system SHALL support authentication against a single AD domain. Multi-domain forest traversal is out of scope for version 1.0. |
| IR-AD-003 | The system SHALL perform a two-step authentication: (1) service account bind + user DN search, (2) user DN bind with submitted password. |
| IR-AD-004 | The LDAP search base DN, user search filter, and group search base DN SHALL all be configurable without redeployment. |
| IR-AD-005 | The system SHALL read the following user attributes from AD: sAMAccountName, userPrincipalName, displayName, mail, department, title, objectGUID, memberOf. |
| IR-AD-006 | The system SHALL support AD group membership lookup using the `memberOf` attribute with configurable recursive group expansion (one level by default). |
| IR-AD-007 | Connection pooling SHALL be used for the service account LDAP connection. Pool size and idle timeout SHALL be configurable. |
| IR-AD-008 | The system SHALL support configuration of a primary and at least one failover domain controller. The failover list SHALL be tried in configured priority order. |

### 12.2 Secrets Management

| ID | Requirement |
|---|---|
| IR-SM-001 | The system SHALL retrieve the AD service account bind password from an approved secrets management solution at application startup (or on-demand with short-lived caching). |
| IR-SM-002 | The system SHALL support HashiCorp Vault as the primary secrets management integration. Alternative implementations (AWS Secrets Manager, Azure Key Vault) SHALL be supportable via a pluggable interface. |
| IR-SM-003 | If the secrets management solution is unavailable at startup, the application SHALL fail to start with a clear error message rather than falling back to a configuration file. |

### 12.3 PKI-RA Internal Modules

| ID | Requirement |
|---|---|
| IR-INT-001 | The authentication module SHALL publish the authenticated user's identity and roles to downstream PKI-RA modules via the Spring Security context (SecurityContextHolder), not via custom propagation mechanisms. |
| IR-INT-002 | The authentication audit log SHALL use the same database connection pool as other PKI-RA modules, with a dedicated schema partition for authentication events. |
| IR-INT-003 | The session management component SHALL expose a programmatic API for other modules to: validate a session, retrieve session details, and force-terminate a session. |

### 12.4 Monitoring & Alerting

| ID | Requirement |
|---|---|
| IR-MON-001 | The system SHALL expose authentication metrics via Spring Boot Actuator / Micrometer: login_attempts_total (labelled by outcome), active_sessions_gauge, ldap_latency_seconds histogram. |
| IR-MON-002 | The system SHALL integrate with the organisation's SIEM by writing structured JSON audit events to a log appender compatible with the existing log aggregation pipeline. |
| IR-MON-003 | The health check endpoint (/actuator/health) SHALL include a custom LDAP health indicator reporting connectivity to configured DCs. |

---

## 13. Compliance & Regulatory Requirements

| ID | Requirement | Source |
|---|---|---|
| CR-001 | All authentication events SHALL be logged with sufficient granularity to support forensic investigation and regulatory audit enquiries. | Internal audit policy / ISO 27001 A.9.4.2 |
| CR-002 | Authentication audit records SHALL be retained for a minimum of 3 years. The retention period SHALL be configurable to accommodate jurisdictional variation. | Internal retention policy |
| CR-003 | User personal data fetched from Active Directory (name, email, department) SHALL be processed only to the extent necessary for the authentication and authorisation purpose (data minimisation). | GDPR Art. 5(1)(c) / PDPA |
| CR-004 | The system SHALL provide a mechanism to anonymise or delete a specific user's PKI-RA profile and associated session/authentication records upon a validated data subject access or erasure request, except where retention is required by law. | GDPR Art. 17 |
| CR-005 | The system SHALL enforce separation of duties: a user with the RA_OPERATOR role SHALL NOT be able to access AD configuration or session management functions. | ISO 27001 A.6.1.2 / SOX where applicable |
| CR-006 | All privileged administrative actions (AD config changes, forced session termination, group mapping changes) SHALL be logged with the administrator's identity, timestamp, and previous/new values. | Change management audit |
| CR-007 | The use of LDAPS (encrypted channel for credential transmission) SHALL satisfy the organisation's requirements for protection of authentication credentials in transit as required by the network security policy. | Network security policy |
| CR-008 | PKI-RA authentication SHALL be included in the organisation's annual information security review and penetration testing scope. | ISO 27001 A.18.2 |

---

## 14. User Interface Requirements

### 14.1 Login Page

| ID | Requirement |
|---|---|
| UIR-001 | The login page SHALL present a single form with fields for Username (supporting both SAMAccountName and UPN formats) and Password. |
| UIR-002 | The login page SHALL display the organisation logo and a clear title identifying the system as PKI-RA. |
| UIR-003 | The password field SHALL use type="password" to mask input. A "show/hide password" toggle SHALL be available. |
| UIR-004 | Submitted credentials SHALL be transmitted over HTTPS only. The form action SHALL NOT submit to an HTTP endpoint. |
| UIR-005 | After a failed login, the page SHALL display a generic error message ("Invalid credentials or account not authorised") without disclosing whether the username exists. The same message SHALL be shown regardless of the specific failure reason, EXCEPT for account-specific messages (see UIR-006). |
| UIR-006 | When an AD-specific sub-error code is available, the UI SHALL display a specific, actionable message: account disabled, account locked (with help desk contact), password expired (with self-service reset link if available), not authorised for PKI-RA (with AD group membership instructions). |
| UIR-007 | The login form SHALL include CSRF protection (hidden token or same-site cookie). |
| UIR-008 | After successful login, the user SHALL be redirected to the PKI-RA dashboard or the originally requested URL. |
| UIR-009 | The login page SHALL be accessible (WCAG 2.1 Level AA) — keyboard navigable, screen reader compatible, sufficient colour contrast. |

### 14.2 Session & Logout

| ID | Requirement |
|---|---|
| UIR-010 | The authenticated user's display name and current role(s) SHALL be visible in the application header on all screens. |
| UIR-011 | A clearly labelled "Log Out" option SHALL be available on every screen. Logout SHALL invalidate the server-side session and redirect to the login page with a "Logged out successfully" message. |
| UIR-012 | When a session expires due to inactivity, the user SHALL be redirected to the login page with a "Session expired" message. Any in-progress work that can be saved SHALL be preserved. |

### 14.3 Administration Interface

| ID | Requirement |
|---|---|
| UIR-013 | The AD configuration administration screen SHALL mask the service account password field by default, with a reveal option accessible only to RA_ADMIN users. |
| UIR-014 | The "Test Connection" button SHALL display a real-time result: green success indicator with response time, or red failure indicator with the error message and recommended remediation. |
| UIR-015 | The group mapping table SHALL support add, edit, and deactivate actions with inline confirmation dialogs for destructive operations. |
| UIR-016 | The active sessions screen SHALL support pagination and search/filter by username, and SHALL auto-refresh at a configurable interval (default: 60 seconds). |

---

## 15. Data Requirements

### 15.1 Data Entities

| Entity | Description | Primary Key Strategy |
|---|---|---|
| ad_configurations | Stores LDAP/AD connection parameters per configuration set. One active configuration at a time. | UUID |
| ad_group_role_mappings | Maps AD group Distinguished Names or CNs to PKI-RA role names. | UUID |
| ad_user_mappings | PKI-RA user profile created via JIT provisioning on first login. Anchored by AD objectGUID. | UUID |
| ad_auth_sessions | Active session registry. Stores session metadata and SHA-256 hash of JWT jti. | UUID |
| ad_auth_attempts | Append-only log of all authentication attempts. Partitioned by month. | BIGINT AUTO_INCREMENT |

### 15.2 Key Data Rules

| ID | Rule |
|---|---|
| DR-001 | The `ad_user_mappings.ad_object_guid` column SHALL be UNIQUE and SHALL NOT be updated after initial provisioning. It serves as the stable AD identity anchor. |
| DR-002 | The `ad_configurations` table SHALL enforce that only one record has `is_active = TRUE` at any time via application logic and database constraint. |
| DR-003 | The `ad_auth_attempts` table SHALL NOT have a delete or update application-layer API. Records are immutable once written. |
| DR-004 | Session tokens (JWTs) SHALL NOT be stored in the `ad_auth_sessions` table. Only the SHA-256 hash of the `jti` claim SHALL be persisted. |
| DR-005 | All timestamps SHALL be stored as UTC in DATETIME(6) or TIMESTAMP(6) columns. The application layer is responsible for timezone conversion for display. |
| DR-006 | User personal data attributes (display_name, email, department) fetched from AD SHALL be updated on each successful login to reflect current AD values. |
| DR-007 | The `ad_auth_attempts.source_ip` field SHALL store the originating client IP address. For requests behind a reverse proxy, the X-Forwarded-For header SHALL be used, with appropriate validation to prevent spoofing. |

### 15.3 Data Retention

| Data Category | Retention Period | Disposal Method |
|---|---|---|
| Authentication attempt logs (ad_auth_attempts) | 3 years minimum | Partition drop after retention period |
| Active session records (ad_auth_sessions) | Duration of session + 30 days | Automated purge job |
| User profile records (ad_user_mappings) | Duration of employment + 90 days after account disabled | Anonymisation on data erasure request |
| AD configuration history | Indefinite (configuration audit) | Manual review only |

---

## 16. Operational Requirements

### 16.1 Deployment

| ID | Requirement |
|---|---|
| OR-001 | The AD authentication module SHALL be deployable as part of the PKI-RA application artifact without requiring separate deployment steps. |
| OR-002 | Database schema migrations for AD authentication tables SHALL be managed by Flyway, executed automatically at application startup. |
| OR-003 | The application SHALL support a configurable connection health check at startup that validates LDAP connectivity before accepting requests. |
| OR-004 | Environment-specific configuration (DC hostnames, base DNs, timeouts) SHALL be provided via Spring Boot profiles (application-prod.yml, application-dev.yml) or environment variables, never hard-coded. |

### 16.2 Monitoring

| ID | Requirement |
|---|---|
| OR-005 | The following metrics SHALL be monitored and alerted on: authentication failure rate > 5% over 5 minutes; LDAP response time P95 > 2 seconds; active session count anomaly (> 2x baseline); failed login burst per user (> 5 in 10 minutes). |
| OR-006 | A runbook SHALL be produced covering: LDAP connectivity failure diagnosis, service account password rotation procedure, DC failover testing, session table maintenance. |
| OR-007 | Log levels for the authentication module SHALL be independently configurable (INFO for production, DEBUG for troubleshooting) without restart via Spring Boot Actuator loggers endpoint. |

### 16.3 Backup & Recovery

| ID | Requirement |
|---|---|
| OR-008 | The authentication database tables SHALL be included in the PKI-RA standard database backup schedule (daily full, hourly incremental). |
| OR-009 | Recovery of the AD configuration table SHALL be tested as part of the disaster recovery exercise, with a target Recovery Time Objective (RTO) of ≤ 4 hours. |

---

## 17. Transition Requirements

| ID | Requirement |
|---|---|
| TR-001 | Existing PKI-RA users with local application accounts SHALL be migrated to AD-backed accounts. Each local account SHALL be matched to its AD counterpart by email address (mail attribute). The migration SHALL be executed as a one-time data migration script, audited and reviewed before execution. |
| TR-002 | During the transition period, a dual-authentication mode SHALL NOT be supported. The cutover to AD authentication SHALL be a single, planned switch-over event with no rollback to local auth except via a defined rollback plan. |
| TR-003 | A rollback plan SHALL be documented and tested, enabling reversion to the previous authentication mechanism within 2 hours if critical issues are discovered post-cutover. |
| TR-004 | All users SHALL be notified at least 5 business days before cutover, with instructions on how to use their corporate credentials to log in. |
| TR-005 | The Help Desk SHALL be briefed on the new authentication mechanism and common failure scenarios at least 3 business days before cutover. |
| TR-006 | A hypercare period of 5 business days following cutover SHALL be observed, during which the development team will be available for rapid-response support. |
| TR-007 | Post-migration, the local user credential store SHALL be disabled and subsequently removed after a 30-day retention period. |

---

## 18. Acceptance Criteria

### 18.1 Functional Acceptance Tests

| ID | Test Scenario | Expected Outcome | Pass / Fail |
|---|---|---|---|
| AC-001 | Valid AD user in PKI_RA_Operators group logs in with correct credentials | Login succeeds; user redirected to dashboard with RA_OPERATOR role | — |
| AC-002 | Valid AD user NOT in any PKI-RA group attempts login | Login rejected with "Not authorised for PKI-RA" message; attempt logged | — |
| AC-003 | User submits correct username but wrong password | Login rejected with generic error; attempt logged with FAILURE outcome | — |
| AC-004 | AD account is disabled; user attempts login | Login rejected with "Account disabled" message; attempt logged | — |
| AC-005 | AD account is locked out; user attempts login | Login rejected with "Account locked" message; attempt logged | — |
| AC-006 | User achieves 5 failed logins within 10 minutes | 6th attempt blocked at application layer regardless of AD outcome; alert generated | — |
| AC-007 | User logs in; AD group membership is then changed; user logs out and logs in again | New roles reflect updated AD group membership | — |
| AC-008 | Primary DC is taken offline; user attempts login | Failover to secondary DC occurs within 10 seconds; login succeeds | — |
| AC-009 | First-time login by a user with no PKI-RA profile | JIT provisioning creates user profile; login succeeds | — |
| AC-010 | User changes their display name in AD; logs out and logs in | PKI-RA profile shows updated display name | — |
| AC-011 | RA Admin configures new DC address and clicks "Test Connection" | Connection result (success/failure) displayed within 5 seconds | — |
| AC-012 | RA Admin force-terminates an active user session | Target user's next request returns 401 Unauthorized | — |
| AC-013 | RA Admin adds a new AD group → RA_VIEWER role mapping | User in new AD group can log in with RA_VIEWER role | — |
| AC-014 | User logs out explicitly | Session record marked inactive; JWT jti blocklisted; redirect to login page | — |
| AC-015 | Session idle timeout (configurable, set to 1 minute for test) elapses | User redirected to login with "Session expired" message | — |

### 18.2 Non-Functional Acceptance Tests

| ID | Test Scenario | Expected Outcome |
|---|---|---|
| AC-NFR-001 | 50 concurrent login requests against a test AD | P95 login response time ≤ 3 seconds; no errors |
| AC-NFR-002 | Plain LDAP (port 389) connection attempted | Connection refused / error; LDAPS-only enforced |
| AC-NFR-003 | JWT with expired exp claim submitted | Request rejected with 401; jti not added to blocklist (token already expired) |
| AC-NFR-004 | Tampered JWT (signature invalid) submitted | Request rejected with 401 |
| AC-NFR-005 | Service account bind password fetched from Vault; Vault taken offline mid-session | Existing sessions continue; new logins fail with clear "Configuration unavailable" error |

---

## 19. Out of Scope

| ID | Item | Reason |
|---|---|---|
| OOS-01 | Authentication of external applicants (end entities submitting certificate requests) | External applicants use a separate authentication channel (client certificate or OTP) |
| OOS-02 | OAuth 2.0 / OpenID Connect integration | Future roadmap item; not required for version 1.0 |
| OOS-03 | SAML-based Single Sign-On | Future roadmap item; dependent on corporate IdP rollout |
| OOS-04 | Multi-domain Active Directory forest traversal | Single-domain scope for version 1.0 |
| OOS-05 | Self-service password reset for AD accounts | Managed by the IT Infrastructure team; out of PKI-RA system scope |
| OOS-06 | Biometric or hardware token MFA at the PKI-RA application layer | MFA is enforced at the AD/corporate identity layer, not within PKI-RA |
| OOS-07 | Directory management (creating, modifying, or deleting AD users or groups) | PKI-RA is a consumer of AD; it does not manage the directory |
| OOS-08 | Automated synchronisation / real-time push of AD group changes to revoke in-flight sessions | Immediate revocation is not in scope; change takes effect at next login |
| OOS-09 | Integration with non-Microsoft LDAP directories (OpenLDAP, Oracle DS) | Version 1.0 targets Microsoft AD only |
| OOS-10 | Guest or service account authentication | Only named human users assigned to PKI-RA AD groups are in scope |

---

## 20. Open Issues & Risks

### 20.1 Open Issues

| ID | Issue | Owner | Target Resolution |
|---|---|---|---|
| OI-001 | Service account permissions: minimum required AD permissions need formal sign-off from IT Infrastructure and Security. | IT Infrastructure / IT Security | 2026-05-04 |
| OI-002 | Secrets management integration: the specific Vault path structure and AppRole configuration for PKI-RA has not been agreed with the Platform team. | Platform / DevOps | 2026-05-04 |
| OI-003 | AD group naming convention: PKI-RA group names (PKI_RA_Admins, PKI_RA_Operators, PKI_RA_Auditors) require formal approval from IT Infrastructure. | IT Infrastructure / PKI Ops | 2026-04-28 |
| OI-004 | SIEM integration format: the structured log format for authentication events requires alignment with the Security Operations team's ingestion pipeline. | Security / Dev Team | 2026-05-11 |
| OI-005 | Migration script for existing local accounts: the matching strategy (by email) needs validation — some legacy accounts may not have a matching AD account. | Dev Team / IT Infrastructure | 2026-05-11 |
| OI-006 | Rollback plan approval: the documented rollback procedure must be reviewed and signed off by IT Security before cutover date. | IT Security / PKI Ops | 2026-05-18 |

### 20.2 Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-001 | AD service account not provisioned in time for integration testing | Medium | High | Track as D-01; escalate to IT Infrastructure lead if not resolved by 2026-05-04 |
| R-002 | DC SSL certificate not trusted by application server's JVM trust store | Medium | High | Verify CA chain early in dev environment; include in dev environment setup checklist |
| R-003 | AD lockout policy more aggressive than expected, causing operational disruption during migration | Low | High | Agree lockout parameters with IT Security before cutover; configure app-level rate limiting as buffer |
| R-004 | Performance degradation during concurrent login spikes (e.g. start of business) | Low | Medium | Load test with 50+ concurrent logins; implement LDAP connection pooling |
| R-005 | objectGUID mismatch between test and production AD (different domains) | Medium | Medium | Use separate AD configuration profiles per environment; test JIT provisioning in UAT against prod-equivalent AD |
| R-006 | Data migration fails for legacy accounts with no AD match | Medium | Medium | Pre-migration audit to identify unmatched accounts; manual remediation plan for residual cases |
| R-007 | Secrets management (Vault) unavailable at application startup | Low | High | Implement startup health gate with clear error; document recovery procedure in runbook |

---

## 21. Glossary

| Term | Definition |
|---|---|
| **Agentic loop** | Iterative AI processing pattern where the model calls tools, receives results, and continues reasoning until task completion |
| **Bind DN** | The Distinguished Name used by the application's service account to authenticate to the LDAP directory |
| **Blocklist (JWT)** | A registry of invalidated JWT jti values that have been explicitly revoked (logout, force-terminate) before their natural expiry |
| **CA** | Certificate Authority — the trusted entity that signs and issues digital certificates |
| **Credential sprawl** | The security risk arising from users having multiple independent sets of credentials across different systems |
| **Distinguished Name (DN)** | The unique, full identifier for an object in an LDAP directory, e.g. CN=jsmith,OU=Users,DC=corp,DC=example,DC=com |
| **Domain Controller (DC)** | A Windows server that hosts Active Directory and processes authentication requests for the domain |
| **Failover** | The automatic switching of a system to a redundant backup when the primary system becomes unavailable |
| **GDPR** | General Data Protection Regulation — EU regulation governing personal data processing |
| **HashiCorp Vault** | An open-source secrets management platform used to store and manage sensitive credentials and secrets |
| **HttpOnly** | A cookie attribute that prevents client-side scripts from accessing the cookie value, mitigating XSS attacks |
| **HyperCare** | An elevated support period immediately following a major system change, with rapid-response availability |
| **IAM** | Identity and Access Management — the framework of policies and technologies ensuring the right individuals have appropriate access |
| **Idempotency** | The property of an operation where applying it multiple times produces the same result as applying it once |
| **Joiner-Mover-Leaver (JML)** | The identity lifecycle process covering employee onboarding, role changes, and offboarding |
| **JWT (JSON Web Token)** | A compact, URL-safe token format for securely representing claims between parties, signed to ensure integrity |
| **jti** | JWT ID — a unique identifier claim within a JWT used for token tracking and revocation |
| **LDAP Injection** | An attack technique where malicious LDAP filter characters in user input manipulate directory queries |
| **LDAP Pool** | A set of pre-established LDAP connections maintained for reuse, reducing the overhead of creating a new connection per request |
| **MFA** | Multi-Factor Authentication — requiring two or more independent credentials (something you know, have, or are) |
| **objectGUID** | A 128-bit GUID assigned by Active Directory to each object; unique, immutable, and stable across renames |
| **Orphaned account** | A user account that remains active in a system after the user's employment or access rights have ended |
| **PDPA** | Personal Data Protection Act — data protection legislation applicable in several Asian jurisdictions |
| **PKI** | Public Key Infrastructure — the set of roles, policies, and procedures for managing digital certificates and public-key encryption |
| **RA** | Registration Authority — the entity that verifies certificate applicant identity before directing the CA to issue a certificate |
| **RACI** | Responsible, Accountable, Consulted, Informed — a responsibility assignment matrix |
| **Rate limiting** | Controlling the number of requests a user or IP can make in a given time period to prevent abuse |
| **SAMAccountName** | Security Account Manager Account Name — the legacy Windows logon name (domain\username format) |
| **SameSite** | A cookie attribute that controls whether cookies are sent with cross-site requests, mitigating CSRF attacks |
| **SIEM** | Security Information and Event Management — a system that aggregates and analyses security event data |
| **SOX** | Sarbanes-Oxley Act — US legislation imposing requirements on financial controls and IT systems supporting financial reporting |
| **Spring Security** | A Java framework providing comprehensive security services for Spring-based applications |
| **TLS** | Transport Layer Security — cryptographic protocol providing encrypted communication over a network |
| **Two-step bind** | The LDAP authentication pattern: (1) service account bind to search for user DN, (2) user DN bind to verify credentials |
| **UPN** | User Principal Name — the modern AD login format, structured as user@domain.com |
| **Vault AppRole** | A HashiCorp Vault authentication method designed for machine-to-machine authentication using role IDs and secret IDs |
| **WCAG** | Web Content Accessibility Guidelines — internationally recognised standards for web content accessibility |

---

*End of Document — PKI-RA-BRS-001 v1.0*
