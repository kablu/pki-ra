package com.pki.ra.raservice.controller;

import com.pki.ra.common.util.AuditLogRepository;
import com.pki.ra.common.util.dto.AuditLogDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin REST endpoints for querying the {@code audit_log} table.
 *
 * <p>Every significant action in the system (config refresh, cert request,
 * cert approval, login, etc.) is recorded in {@code audit_log} via
 * {@link com.pki.ra.common.util.AuditLogService}. These endpoints let
 * administrators query the full audit trail.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *  GET /api/admin/audit-logs                             paginated, newest-first
 *  GET /api/admin/audit-logs/user/{username}             paginated by user
 *  GET /api/admin/audit-logs/action/{action}             paginated by action
 *  GET /api/admin/audit-logs/outcome/{outcome}           paginated by outcome
 *  GET /api/admin/audit-logs/user/{username}/failures    failures for one user
 *  GET /api/admin/audit-logs/summary/actions             action counts (dashboard)
 *  GET /api/admin/audit-logs/summary/outcomes            outcome counts (dashboard)
 * </pre>
 *
 * <p>Security: all endpoints are protected by {@code ROLE_ADMIN} via
 * {@code AdminSecurityConfig} ({@code /api/admin/**}).
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    /** Maximum entries per page — prevents accidental full-table dumps. */
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // =========================================================================
    // GET /api/admin/audit-logs?page=0&size=20
    // =========================================================================

    /**
     * Paginated list of all audit entries — newest first.
     *
     * <p>Query params:
     * <ul>
     *   <li>{@code page} — zero-based index (default 0)</li>
     *   <li>{@code size} — entries per page (default 20, max {@value MAX_PAGE_SIZE})</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<Page<AuditLogDto>> getAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));

        Page<AuditLogDto> result = auditLogRepository
                .findAllByOrderByCreatedAtDesc(pageable)
                .map(AuditLogDto::from);

        log.debug("GET /audit-logs — page={} size={} total={}",
                  page, pageable.getPageSize(), result.getTotalElements());

        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // GET /api/admin/audit-logs/user/{username}?page=0&size=20
    // =========================================================================

    /**
     * Paginated audit entries for a specific user — newest first.
     *
     * <p>Answers: "What did {@code john.doe} do, and when?"
     *
     * <p>Example: {@code GET /api/admin/audit-logs/user/admin?size=10}
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<Page<AuditLogDto>> getByUser(
            @PathVariable String username,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));

        Page<AuditLogDto> result = auditLogRepository
                .findByUsernameOrderByCreatedAtDesc(username, pageable)
                .map(AuditLogDto::from);

        log.debug("GET /audit-logs/user/{} — page={} total={}",
                  username, page, result.getTotalElements());

        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // GET /api/admin/audit-logs/action/{action}?page=0&size=20
    // =========================================================================

    /**
     * Paginated audit entries for a specific action type — newest first.
     *
     * <p>Answers: "Every time {@code CONFIG_REFRESH} was called, who did it?"
     *
     * <p>Common action constants:
     * <ul>
     *   <li>{@code CONFIG_REFRESH}  — admin hot-reloaded config cache</li>
     *   <li>{@code CERT_REQUEST}    — CSR submitted</li>
     *   <li>{@code CERT_APPROVE}    — CSR approved by RA officer</li>
     *   <li>{@code CERT_REVOKE}     — certificate revoked</li>
     *   <li>{@code LOGIN}           — successful user login</li>
     *   <li>{@code LOGOUT}          — user logged out</li>
     * </ul>
     *
     * <p>Example: {@code GET /api/admin/audit-logs/action/CONFIG_REFRESH}
     */
    @GetMapping("/action/{action}")
    public ResponseEntity<Page<AuditLogDto>> getByAction(
            @PathVariable String action,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));

        Page<AuditLogDto> result = auditLogRepository
                .findByActionOrderByCreatedAtDesc(action, pageable)
                .map(AuditLogDto::from);

        log.debug("GET /audit-logs/action/{} — page={} total={}",
                  action, page, result.getTotalElements());

        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // GET /api/admin/audit-logs/outcome/{outcome}?page=0&size=20
    // =========================================================================

    /**
     * Paginated audit entries filtered by outcome — newest first.
     *
     * <p>Answers: "Show me every failed operation in the system."
     *
     * <p>Example: {@code GET /api/admin/audit-logs/outcome/FAILURE}
     *
     * @param outcome {@code SUCCESS} or {@code FAILURE} (case-insensitive)
     */
    @GetMapping("/outcome/{outcome}")
    public ResponseEntity<Page<AuditLogDto>> getByOutcome(
            @PathVariable String outcome,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));

        Page<AuditLogDto> result = auditLogRepository
                .findByOutcomeOrderByCreatedAtDesc(outcome.toUpperCase(), pageable)
                .map(AuditLogDto::from);

        log.debug("GET /audit-logs/outcome/{} — page={} total={}",
                  outcome, page, result.getTotalElements());

        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // GET /api/admin/audit-logs/user/{username}/failures
    // =========================================================================

    /**
     * All failed actions for a specific user — newest first (non-paginated).
     *
     * <p>Answers: "Has {@code john.doe} had any failures recently?"
     * Typically a small set so pagination is not applied here.
     *
     * <p>Example: {@code GET /api/admin/audit-logs/user/john.doe/failures}
     */
    @GetMapping("/user/{username}/failures")
    public ResponseEntity<List<AuditLogDto>> getFailuresByUser(@PathVariable String username) {
        List<AuditLogDto> entries = auditLogRepository
                .findByUsernameAndOutcomeOrderByCreatedAtDesc(username, "FAILURE")
                .stream()
                .map(AuditLogDto::from)
                .toList();

        log.debug("GET /audit-logs/user/{}/failures — {} entries", username, entries.size());
        return ResponseEntity.ok(entries);
    }

    // =========================================================================
    // GET /api/admin/audit-logs/summary/actions
    // =========================================================================

    /**
     * Action-level entry counts — ordered by count descending.
     *
     * <p>Uses {@link com.pki.ra.common.util.dto.ActionSummaryProjection} —
     * fully type-safe, no {@code Object[]} casting.
     *
     * <p>Example response:
     * <pre>{@code
     * { "LOGIN": 203, "CERT_REQUEST": 45, "CONFIG_REFRESH": 12 }
     * }</pre>
     */
    @GetMapping("/summary/actions")
    public ResponseEntity<Map<String, Long>> getSummaryByAction() {
        Map<String, Long> summary = new LinkedHashMap<>();
        auditLogRepository.summariseByAction()
                .forEach(p -> summary.put(p.getAction(), p.getCount()));

        log.debug("GET /audit-logs/summary/actions — {} distinct actions", summary.size());
        return ResponseEntity.ok(summary);
    }

    // =========================================================================
    // GET /api/admin/audit-logs/summary/outcomes
    // =========================================================================

    /**
     * Outcome-level entry counts — quick system health indicator.
     *
     * <p>Example response:
     * <pre>{@code
     * { "SUCCESS": 950, "FAILURE": 12 }
     * }</pre>
     */
    @GetMapping("/summary/outcomes")
    public ResponseEntity<Map<String, Long>> getSummaryByOutcome() {
        Map<String, Long> summary = new LinkedHashMap<>();
        auditLogRepository.summariseByOutcome()
                .forEach(p -> summary.put(p.getAction(), p.getCount()));

        log.debug("GET /audit-logs/summary/outcomes — {} outcomes", summary.size());
        return ResponseEntity.ok(summary);
    }
}
