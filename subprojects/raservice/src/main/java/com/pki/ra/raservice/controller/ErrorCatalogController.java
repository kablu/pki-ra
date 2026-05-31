package com.pki.ra.raservice.controller;

import com.pki.ra.common.config.Refreshable;
import com.pki.ra.common.error.ErrorCatalogBean;
import com.pki.ra.common.user.service.UserLookupService;
import com.pki.ra.common.util.AuditLogService;
import com.pki.ra.common.web.AbstractRefreshController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST endpoint for hot-reloading the {@code error_catalog} cache.
 *
 * <h3>Endpoint</h3>
 * {@code POST /api/admin/error-catalog/refresh} — reloads all active
 * {@code error_catalog} rows from DB into {@link ErrorCatalogBean} in-memory cache.
 * No application restart required.
 *
 * <h3>When to call</h3>
 * <ul>
 *   <li>After inserting or updating error rows directly in the DB.</li>
 *   <li>After Flyway migration adds new error codes on a running instance.</li>
 *   <li>After toggling {@code is_active} on any row.</li>
 * </ul>
 *
 * <h3>Design</h3>
 * Full refresh flow (auth check, audit log, IP extraction, error handling)
 * is inherited from {@link AbstractRefreshController} — zero logic here.
 *
 * @see AbstractRefreshController
 * @see ErrorCatalogBean
 * @author pki-ra
 * @since  1.0.0
 */
@RestController
@RequestMapping("/api/admin/error-catalog")
public class ErrorCatalogController extends AbstractRefreshController {

    private final Refreshable errorCatalogBean;

    public ErrorCatalogController(ErrorCatalogBean errorCatalogBean,
                                  AuditLogService auditLogService,
                                  UserLookupService userLookupService) {
        super(auditLogService, userLookupService);
        this.errorCatalogBean = errorCatalogBean;
    }

    @Override
    protected Refreshable refreshableService() {
        return errorCatalogBean;
    }

    // POST /api/admin/error-catalog/refresh → fully handled by AbstractRefreshController
}
