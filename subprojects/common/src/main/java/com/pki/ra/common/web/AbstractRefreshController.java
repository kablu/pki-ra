package com.pki.ra.common.web;

import com.pki.ra.common.config.Refreshable;
import com.pki.ra.common.config.dto.RefreshResult;
import com.pki.ra.common.user.service.UserLookupService;
import com.pki.ra.common.util.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Abstract base controller providing a complete {@code POST /refresh} endpoint
 * for any hot-reloadable cache — defined once, reused by every module.
 *
 * <h3>Design Pattern — Template Method</h3>
 * Full refresh flow defined here once:
 * <ol>
 *   <li>Resolve username, userId, and client IP via
 *       {@link AbstractSecuredController#resolveAuditContext(HttpServletRequest)}.</li>
 *   <li>Read service metadata ({@code auditAction}, {@code resourceId}) from {@link Refreshable}.</li>
 *   <li>Call {@link Refreshable#refresh(String)}.</li>
 *   <li>Write {@code SUCCESS} audit entry (with pre-resolved {@code userId}) on success.</li>
 *   <li>Return {@code 200 OK} with {@link RefreshResult}.</li>
 *   <li>On exception: write {@code FAILURE} audit entry, log error, re-throw.</li>
 * </ol>
 * Subclasses supply only the {@link Refreshable} bean via
 * {@link #refreshableService()} — all steps are inherited.
 *
 * <h3>Hierarchy</h3>
 * <pre>
 *   AbstractSecuredController   ← username / userId / IP resolution; auditLogService
 *   └── AbstractRefreshController  ← refresh flow (Template Method)
 *       └── ConfigController, ErrorCatalogController, …
 * </pre>
 *
 * <h3>Adding a new module's refresh endpoint — 2 steps only</h3>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/admin/cmp-config")
 * public class CmpConfigController extends AbstractRefreshController {
 *
 *     private final Refreshable cmpConfigBean;
 *
 *     public CmpConfigController(
 *             @Qualifier("cmpConfigBean") Refreshable cmpConfigBean,
 *             AuditLogService auditLogService,
 *             UserLookupService userLookupService) {
 *         super(auditLogService, userLookupService);
 *         this.cmpConfigBean = cmpConfigBean;
 *     }
 *
 *     @Override
 *     protected Refreshable refreshableService() { return cmpConfigBean; }
 *     // POST /api/admin/cmp-config/refresh is live — nothing else needed
 * }
 * }</pre>
 *
 * @see AbstractSecuredController
 * @see Refreshable
 * @see RefreshResult
 */
public abstract class AbstractRefreshController extends AbstractSecuredController {

    private static final Logger log =
            LoggerFactory.getLogger(AbstractRefreshController.class);

    /**
     * Constructor for subclasses — receives both services from Spring.
     *
     * @param auditLogService   writes SUCCESS / FAILURE audit entries — never null
     * @param userLookupService resolves numeric userId from AD username — never null
     */
    protected AbstractRefreshController(AuditLogService auditLogService,
                                        UserLookupService userLookupService) {
        super(auditLogService, userLookupService);
    }

    // =========================================================================
    // Template hook — subclass supplies the Refreshable bean
    // =========================================================================

    /**
     * Returns the {@link Refreshable} bean managed by this controller.
     * Each subclass injects its own bean and returns it here.
     *
     * <p>Called once per request — simply return the injected field,
     * no logic or side-effects.
     *
     * @return the {@link Refreshable} bean — never null
     */
    protected abstract Refreshable refreshableService();

    // =========================================================================
    // POST /refresh — complete flow, inherited by all subclasses
    // =========================================================================

    /**
     * Hot-reloads the cache managed by {@link #refreshableService()}.
     *
     * <p>Marked {@code final} — audit and error-handling contract
     * must not be bypassed by subclasses.
     *
     * @param request inbound HTTP request — for client IP resolution only
     * @return {@code 200 OK} with {@link RefreshResult} on success;
     *         exceptions re-thrown to global {@code @ControllerAdvice}
     */
    @PostMapping("/refresh")
    public final ResponseEntity<RefreshResult> refresh(HttpServletRequest request) {

        Refreshable  service    = refreshableService();
        AuditContext ctx        = resolveAuditContext(request);   // single call — all three values
        String       action     = service.getAuditAction();       // e.g. "APP_CONFIG_REFRESH"
        String       resourceId = service.getResourceId();        // e.g. "app_config"
        // action + resourceId captured before refresh() — safe in catch block if refresh() throws

        log.info("[{}] refresh requested — userId='{}' username='{}' ip='{}'",
                 action, ctx.userId(), ctx.username(), ctx.ip());

        try {
            RefreshResult result = service.refresh(ctx.username());

            auditLogService.logSuccess(
                    ctx.username(),
                    action,
                    resourceId,
                    result.count() + " item(s) loaded into " + result.serviceName(),
                    ctx.ip(),
                    ctx.userId()   // pre-resolved — no second DB call inside AuditLogService
            );

            log.info("[{}] refresh complete — count={} userId='{}' username='{}' ip='{}'",
                     action, result.count(), ctx.userId(), ctx.username(), ctx.ip());

            return ResponseEntity.ok(result);

        } catch (Exception ex) {

            auditLogService.logFailure(
                    ctx.username(),
                    action,
                    resourceId,
                    "Refresh failed: " + ex.getMessage(),
                    ctx.ip(),
                    ctx.userId()   // pre-resolved — no second DB call inside AuditLogService
            );

            log.error("[{}] refresh FAILED — userId='{}' username='{}' reason='{}'",
                      action, ctx.userId(), ctx.username(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
