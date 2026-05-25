package com.pki.ra.raservice.controller;

import com.pki.ra.common.config.AppConfigRepository;
import com.pki.ra.common.config.ConfigBean;
import com.pki.ra.common.config.dto.AppConfigAuditDto;
import com.pki.ra.common.config.dto.ConfigRefreshResponse;
import com.pki.ra.common.util.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

/**
 * Admin REST endpoints for {@code app_config} cache management.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/admin/config/refresh} — hot-reload ConfigBean cache from DB;
 *       writes a {@code CONFIG_REFRESH} entry to the {@code audit_log} table.</li>
 *   <li>{@code GET  /api/admin/config}         — returns every {@code app_config} row
 *       with its full {@code BaseAuditEntity} audit trail
 *       ({@code created_by}, {@code created_at}, {@code updated_by}, {@code updated_at}).</li>
 * </ul>
 *
 * <p>Security: both endpoints are protected by {@code ROLE_ADMIN} via
 * {@code AdminSecurityConfig}. Unauthorized → 401; non-admin → 403.
 */
@RestController
@RequestMapping("/api/admin/config")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    /** Action constant written to {@code audit_log.action} on every refresh. */
    private static final String ACTION_CONFIG_REFRESH = "CONFIG_REFRESH";

    private final ConfigBean           configBean;
    private final AppConfigRepository  appConfigRepository;
    private final AuditLogService      auditLogService;

    public ConfigController(ConfigBean configBean,
                            AppConfigRepository appConfigRepository,
                            AuditLogService auditLogService) {
        this.configBean          = configBean;
        this.appConfigRepository = appConfigRepository;
        this.auditLogService     = auditLogService;
    }

    // -------------------------------------------------------------------------
    // POST /api/admin/config/refresh
    // -------------------------------------------------------------------------

    /**
     * Hot-reloads the {@link ConfigBean} in-memory cache from the database.
     *
     * <p>Full flow:
     * <ol>
     *   <li>Admin updates rows in {@code app_config} (DB tool / migration).</li>
     *   <li>Calls this endpoint — no application restart needed.</li>
     *   <li>Cache is cleared and reloaded; a {@code CONFIG_REFRESH} audit entry is written.</li>
     *   <li>All services reading {@code configBean.getValue(key)} see new values immediately.</li>
     * </ol>
     *
     * <p>Audit fields written to {@code audit_log}:
     * <ul>
     *   <li>{@code username}    — authenticated principal (e.g. {@code admin})</li>
     *   <li>{@code action}      — {@code CONFIG_REFRESH}</li>
     *   <li>{@code resourceId}  — {@code app_config} (table name)</li>
     *   <li>{@code description} — count of active rows after reload</li>
     *   <li>{@code ip_address}  — caller's remote IP</li>
     *   <li>{@code outcome}     — {@code SUCCESS} or {@code FAILURE}</li>
     * </ul>
     *
     * @param authentication Spring Security principal — always present (filter enforces auth)
     * @param request        HTTP request used to extract caller IP for the audit entry
     * @return 200 OK with {@link ConfigRefreshResponse}
     */
    @PostMapping("/refresh")
    public ResponseEntity<ConfigRefreshResponse> refresh(Authentication authentication,
                                                         HttpServletRequest request) {
        String username  = authentication.getName();
        String ipAddress = resolveClientIp(request);

        log.info("Config refresh requested — user='{}' ip='{}'", username, ipAddress);

        try {
            ConfigRefreshResponse response = configBean.refresh(username);

            auditLogService.logSuccess(
                    username,
                    ACTION_CONFIG_REFRESH,
                    "app_config",
                    response.count() + " active row(s) loaded into ConfigBean cache",
                    ipAddress
            );

            log.info("Config refresh complete — count={} triggeredBy='{}' ip='{}'",
                     response.count(), username, ipAddress);

            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            auditLogService.logFailure(
                    username,
                    ACTION_CONFIG_REFRESH,
                    "app_config",
                    "Refresh failed: " + ex.getMessage(),
                    ipAddress
            );
            log.error("Config refresh FAILED — user='{}' reason='{}'", username, ex.getMessage(), ex);
            throw ex;
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/config
    // -------------------------------------------------------------------------

    /**
     * Returns all {@code app_config} rows with their full audit trail.
     *
     * <p>Each entry includes the four {@code BaseAuditEntity} columns:
     * <ul>
     *   <li>{@code createdBy}  / {@code createdAt}  — who inserted the row and when</li>
     *   <li>{@code updatedBy}  / {@code updatedAt}  — who last changed the row and when</li>
     * </ul>
     *
     * <p>Use this endpoint to answer questions such as:
     * <ul>
     *   <li>"Who changed the LDAP bind password, and when?"</li>
     *   <li>"Was this row seeded by the system or updated by a human?"</li>
     * </ul>
     *
     * @return 200 OK with list of {@link AppConfigAuditDto}
     *         (both active and inactive rows are returned — filter by {@code isActive} client-side)
     */
    @GetMapping
    public ResponseEntity<List<AppConfigAuditDto>> getAllWithAudit() {
        List<AppConfigAuditDto> rows = appConfigRepository.findAll()
                .stream()
                .map(AppConfigAuditDto::from)
                .toList();

        log.debug("GET /api/admin/config — returning {} row(s) with audit fields", rows.size());
        return ResponseEntity.ok(rows);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the real client IP — checks {@code X-Forwarded-For} first
     * (set by reverse proxies / load balancers), falls back to remote address.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim(); // first IP in the chain = real client
        }
        return request.getRemoteAddr();
    }
}
