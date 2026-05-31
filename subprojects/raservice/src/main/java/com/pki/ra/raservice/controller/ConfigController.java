package com.pki.ra.raservice.controller;

import com.pki.ra.common.config.AppConfigRepository;
import com.pki.ra.common.config.Refreshable;
import com.pki.ra.common.config.dto.AppConfigAuditDto;
import com.pki.ra.common.util.AuditLogService;
import com.pki.ra.common.util.UserLookupService;
import com.pki.ra.common.web.AbstractRefreshController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin REST endpoints for {@code app_config} cache management.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/admin/config/refresh} — hot-reloads the
 *       {@link com.pki.ra.common.config.ConfigBean} cache from DB.
 *       <strong>Fully inherited from {@link AbstractRefreshController}</strong> —
 *       no code needed here. Audit logging, IP extraction, error handling
 *       all inherited.</li>
 *   <li>{@code GET /api/admin/config} — returns every {@code app_config} row
 *       with its full {@code BaseAuditEntity} audit trail
 *       ({@code created_by}, {@code created_at}, {@code updated_by},
 *       {@code updated_at}).</li>
 * </ul>
 *
 * <h3>Design — Template Method via AbstractRefreshController</h3>
 * This controller only:
 * <ol>
 *   <li>Supplies the {@link Refreshable} bean via {@link #refreshableService()}.</li>
 *   <li>Adds the {@code GET /api/admin/config} endpoint specific to this module.</li>
 * </ol>
 * Adding the same refresh capability to CMP / ACME / Online requires exactly
 * the same 2 steps — zero logic duplication across modules.
 *
 * <p>Security: all endpoints are protected by {@code ROLE_ADMIN} via
 * {@code AdminSecurityConfig}. Unauthorized → 401; non-admin → 403.
 *
 * @see AbstractRefreshController
 * @see com.pki.ra.common.config.Refreshable
 */
@RestController
@RequestMapping("/api/admin/config")
public class ConfigController extends AbstractRefreshController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    private final Refreshable         configService;
    private final AppConfigRepository appConfigRepository;

    public ConfigController(
            Refreshable configService,
            AppConfigRepository appConfigRepository,
            AuditLogService auditLogService,
            UserLookupService userLookupService) {
        super(auditLogService, userLookupService);
        this.configService       = configService;
        this.appConfigRepository = appConfigRepository;
    }

    // =========================================================================
    // Template hook — supply ConfigBean to the base controller
    // =========================================================================

    /**
     * Returns the {@link com.pki.ra.common.config.ConfigBean} that backs the
     * RA app-config cache. Called by {@link AbstractRefreshController} on every
     * {@code POST /refresh} request.
     */
    @Override
    protected Refreshable refreshableService() {
        return configService;
    }

    // POST /api/admin/config/refresh → fully handled by AbstractRefreshController

    // =========================================================================
    // GET /api/admin/config
    // =========================================================================

    /**
     * Returns all {@code app_config} rows with their full audit trail.
     *
     * <p>Each entry exposes the four {@code BaseAuditEntity} columns:
     * <ul>
     *   <li>{@code createdBy} / {@code createdAt} — who inserted the row and when</li>
     *   <li>{@code updatedBy} / {@code updatedAt} — who last changed the row and when</li>
     * </ul>
     *
     * <p>Use this endpoint to answer:
     * <ul>
     *   <li>"Who changed the LDAP bind password, and when?"</li>
     *   <li>"Was this row seeded by the system or updated by a human?"</li>
     * </ul>
     *
     * @return 200 OK with list of {@link AppConfigAuditDto}
     */
    @GetMapping
    public ResponseEntity<List<AppConfigAuditDto>> getAllWithAudit() {
        List<AppConfigAuditDto> rows = appConfigRepository.findAll()
                .stream()
                .map(AppConfigAuditDto::from)
                .toList();

        log.debug("GET /api/admin/config — returning {} row(s) with audit fields",
                  rows.size());
        return ResponseEntity.ok(rows);
    }
}
