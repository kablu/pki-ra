package com.pki.ra.raservice.controller;

import com.pki.ra.common.util.AuditLogRepository;
import com.pki.ra.common.util.dto.AuditLogDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Admin REST endpoints for querying the {@code audit_log} table.
 *
 * <p>Every significant action in the system (config refresh, cert request,
 * cert approval, login, etc.) is recorded in {@code audit_log} via
 * {@link com.pki.ra.common.util.AuditLogService}.
 * These endpoints let administrators query that trail.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /api/admin/audit-logs}                    — paginated, newest-first</li>
 *   <li>{@code GET /api/admin/audit-logs/user/{username}}    — all entries for one user</li>
 *   <li>{@code GET /api/admin/audit-logs/action/{action}}    — all entries for one action</li>
 *   <li>{@code GET /api/admin/audit-logs/outcome/{outcome}}  — SUCCESS or FAILURE entries</li>
 *   <li>{@code GET /api/admin/audit-logs/summary}            — action counts for dashboard</li>
 * </ul>
 *
 * <p>Security: all endpoints protected by {@code ROLE_ADMIN} via
 * {@code AdminSecurityConfig} ({@code /api/admin/**}).
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/audit-logs?page=0&size=20
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of all audit entries — newest first.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code page} — zero-based page index (default: 0)</li>
     *   <li>{@code size} — page size (default: 20, max: 100)</li>
     * </ul>
     *
     * <p>Example: {@code GET /api/admin/audit-logs?page=0&size=10}
     *
     * @param page zero-based page index
     * @param size number of entries per page
     * @return 200 OK with paginated {@link AuditLogDto} entries
     */
    @GetMapping
    public ResponseEntity<Page<AuditLogDto>> getAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(size, 100);  // cap at 100 per page
        Pageable pageable = PageRequest.of(page, safeSize);

        Page<AuditLogDto> result = auditLogRepository
                .findAllByOrderByCreatedAtDesc(pageable)
                .map(AuditLogDto::from);

        log.debug("GET /api/admin/audit-logs — page={} size={} total={}",
                  page, safeSize, result.getTotalElements());

        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/audit-logs/user/{username}
    // -------------------------------------------------------------------------

    /**
     * Returns all audit entries for a specific user — newest first.
     *
     * <p>Useful for answering: "What did user {@code john.doe} do?"
     *
     * <p>Example: {@code GET /api/admin/audit-logs/user/admin}
     *
     * @param username AD sAMAccountName (case-sensitive)
     * @return 200 OK with list of {@link AuditLogDto}
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<List<AuditLogDto>> getByUser(@PathVariable String username) {
        List<AuditLogDto> entries = auditLogRepository
                .findByUsernameOrderByCreatedAtDesc(username)
                .stream()
                .map(AuditLogDto::from)
                .toList();

        log.debug("GET /api/admin/audit-logs/user/{} — {} entries", username, entries.size());
        return ResponseEntity.ok(entries);
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/audit-logs/action/{action}
    // -------------------------------------------------------------------------

    /**
     * Returns all audit entries for a specific action type — newest first.
     *
     * <p>Useful for answering: "Every time CONFIG_REFRESH was called, who did it?"
     *
     * <p>Example: {@code GET /api/admin/audit-logs/action/CONFIG_REFRESH}
     *
     * <p>Common action constants:
     * <ul>
     *   <li>{@code CONFIG_REFRESH}  — admin hot-reloaded config cache</li>
     *   <li>{@code CERT_REQUEST}    — certificate signing request submitted</li>
     *   <li>{@code CERT_APPROVE}    — CSR approved by RA officer</li>
     *   <li>{@code CERT_REVOKE}     — certificate revoked</li>
     *   <li>{@code LOGIN}           — successful user login</li>
     *   <li>{@code LOGOUT}          — user logged out</li>
     * </ul>
     *
     * @param action action type constant (case-sensitive)
     * @return 200 OK with list of {@link AuditLogDto}
     */
    @GetMapping("/action/{action}")
    public ResponseEntity<List<AuditLogDto>> getByAction(@PathVariable String action) {
        List<AuditLogDto> entries = auditLogRepository
                .findByActionOrderByCreatedAtDesc(action)
                .stream()
                .map(AuditLogDto::from)
                .toList();

        log.debug("GET /api/admin/audit-logs/action/{} — {} entries", action, entries.size());
        return ResponseEntity.ok(entries);
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/audit-logs/outcome/{outcome}
    // -------------------------------------------------------------------------

    /**
     * Returns all audit entries with a given outcome — newest first.
     *
     * <p>Useful for answering: "Show me every failed operation in the system."
     *
     * <p>Example: {@code GET /api/admin/audit-logs/outcome/FAILURE}
     *
     * @param outcome {@code SUCCESS} or {@code FAILURE}
     * @return 200 OK with list of {@link AuditLogDto}
     */
    @GetMapping("/outcome/{outcome}")
    public ResponseEntity<List<AuditLogDto>> getByOutcome(@PathVariable String outcome) {
        List<AuditLogDto> entries = auditLogRepository
                .findByOutcomeOrderByCreatedAtDesc(outcome.toUpperCase())
                .stream()
                .map(AuditLogDto::from)
                .toList();

        log.debug("GET /api/admin/audit-logs/outcome/{} — {} entries", outcome, entries.size());
        return ResponseEntity.ok(entries);
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/audit-logs/summary
    // -------------------------------------------------------------------------

    /**
     * Returns action-level counts — useful for admin dashboards.
     *
     * <p>Example response:
     * <pre>{@code
     * {
     *   "CONFIG_REFRESH": 12,
     *   "CERT_REQUEST":   45,
     *   "LOGIN":          203
     * }
     * }</pre>
     *
     * @return 200 OK with action → count map, ordered by count descending
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> getSummary() {
        Map<String, Long> summary = new java.util.LinkedHashMap<>();
        auditLogRepository.countByAction()
                .forEach(row -> summary.put((String) row[0], (Long) row[1]));

        log.debug("GET /api/admin/audit-logs/summary — {} distinct actions", summary.size());
        return ResponseEntity.ok(summary);
    }
}
