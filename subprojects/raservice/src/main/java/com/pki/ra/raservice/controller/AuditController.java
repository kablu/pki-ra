package com.pki.ra.raservice.controller;

import com.pki.ra.common.model.AuditLog;
import com.pki.ra.common.util.AuditLogRepository;
import com.pki.ra.common.util.AuditLogSpecification;
import com.pki.ra.common.util.dto.AuditLogDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin REST endpoints for querying the {@code audit_log} table.
 *
 * <p>Every significant action in the system (config refresh, cert request,
 * cert approval, login, etc.) is recorded in {@code audit_log} via
 * {@link com.pki.ra.common.util.AuditLogService}. These endpoints let
 * administrators query the full audit trail.
 *
 * <h3>Design Pattern — Specification (refactored)</h3>
 * Previously there were 5 separate path-variable endpoints for each filter
 * combination. They are now collapsed into <strong>one</strong> search endpoint
 * that accepts optional query parameters. Filtering is delegated entirely to
 * {@link AuditLogSpecification} composable predicates — no repository changes
 * are needed when a new filter is added.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *  GET /api/admin/audit-logs                                          all (paginated)
 *  GET /api/admin/audit-logs?username=john                            by user
 *  GET /api/admin/audit-logs?action=LOGIN                             by action
 *  GET /api/admin/audit-logs?outcome=FAILURE                         by outcome
 *  GET /api/admin/audit-logs?username=john&amp;outcome=FAILURE         combined
 *  GET /api/admin/audit-logs?resourceId=cert-42                      by resource
 *  GET /api/admin/audit-logs/summary/actions                         action counts
 *  GET /api/admin/audit-logs/summary/outcomes                        outcome counts
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
    // GET /api/admin/audit-logs — unified search with optional filters
    // =========================================================================

    /**
     * Paginated, filtered audit-log search — newest first.
     *
     * <p>All parameters are <strong>optional</strong>. Omitting a parameter means
     * "no filter on that field". Any combination is valid:
     *
     * <ul>
     *   <li>{@code username}   — exact match, e.g. {@code admin}</li>
     *   <li>{@code action}     — exact match, e.g. {@code CONFIG_REFRESH}</li>
     *   <li>{@code outcome}    — {@code SUCCESS} or {@code FAILURE} (case-insensitive)</li>
     *   <li>{@code resourceId} — exact match on the targeted resource</li>
     *   <li>{@code page}       — zero-based page index (default 0)</li>
     *   <li>{@code size}       — entries per page (default 20, max {@value MAX_PAGE_SIZE})</li>
     * </ul>
     *
     * <p>Filtering is composed via {@link AuditLogSpecification} predicates —
     * adding a new filter only requires a new spec method; no new endpoint needed.
     *
     * <p>Example calls:
     * <pre>
     *   GET /api/admin/audit-logs                              → all entries
     *   GET /api/admin/audit-logs?outcome=FAILURE              → all failures
     *   GET /api/admin/audit-logs?username=john&amp;outcome=FAILURE → john's failures
     * </pre>
     */
    @GetMapping
    public ResponseEntity<Page<AuditLogDto>> search(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String resourceId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Specification<AuditLog> spec = Specification
                .where(AuditLogSpecification.hasUsername(username))
                .and(AuditLogSpecification.hasAction(action))
                .and(AuditLogSpecification.hasOutcome(outcome))
                .and(AuditLogSpecification.hasResourceId(resourceId));

        return pagedResponse(
                auditLogRepository.findAll(spec, toPageable(page, size)),
                "username=%s action=%s outcome=%s resourceId=%s"
                        .formatted(username, action, outcome, resourceId));
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

    // =========================================================================
    // Private helpers — DRY
    // =========================================================================

    /**
     * Builds a {@link Pageable} capped at {@value MAX_PAGE_SIZE}, sorted
     * by {@code createdAt} descending (newest first).
     *
     * <p>Centralises the {@code Math.min} + {@link Sort} boilerplate that was
     * previously duplicated in every endpoint method.
     */
    private Pageable toPageable(int page, int size) {
        return PageRequest.of(
                page,
                Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * Maps a {@link Page} of {@link AuditLog} entities to a {@link Page} of
     * {@link AuditLogDto} and wraps it in a {@code 200 OK} response.
     *
     * <p>Also logs the query context string and total element count so every
     * search endpoint gets consistent debug output without repeating the
     * {@code log.debug} call.
     *
     * @param resultPage the raw entity page from the repository
     * @param ctx        short description of active filters (for debug logging)
     */
    private ResponseEntity<Page<AuditLogDto>> pagedResponse(Page<AuditLog> resultPage, String ctx) {
        log.debug("GET /audit-logs [{}] — total={}", ctx, resultPage.getTotalElements());
        return ResponseEntity.ok(resultPage.map(AuditLogDto::from));
    }
}
