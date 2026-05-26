package com.pki.ra.common.web;

import com.pki.ra.common.config.Refreshable;
import com.pki.ra.common.config.dto.RefreshResult;
import com.pki.ra.common.util.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Abstract base controller providing a complete {@code POST /refresh} endpoint
 * for any hot-reloadable cache — defined once, reused by every module.
 *
 * <h3>Design Pattern — Template Method</h3>
 * Full refresh flow defined here once:
 * <ol>
 *   <li>Extract username + client IP from the request.</li>
 *   <li>Read service metadata (auditAction, resourceId) from the {@link Refreshable} bean.</li>
 *   <li>Call {@link Refreshable#refresh(String)}.</li>
 *   <li>Write {@code SUCCESS} audit entry on success.</li>
 *   <li>Return {@code 200 OK} with {@link RefreshResult}.</li>
 *   <li>On exception: write {@code FAILURE} audit entry, log error, re-throw.</li>
 * </ol>
 * Subclasses supply only the {@link Refreshable} bean via
 * {@link #refreshableService()} — all six steps are inherited.
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
 *             AuditLogService auditLogService) {
 *         super(auditLogService);
 *         this.cmpConfigBean = cmpConfigBean;
 *     }
 *
 *     @Override
 *     protected Refreshable refreshableService() { return cmpConfigBean; }
 *     // POST /api/admin/cmp-config/refresh is live — nothing else needed
 * }
 * }</pre>
 *
 * <h3>Why {@code final} on the endpoint method?</h3>
 * Prevents subclasses from accidentally bypassing the audit / error-handling
 * contract. Behaviour is extended only via {@link #refreshableService()}.
 *
 * <h3>Thread safety</h3>
 * This class is stateless — all state lives in the injected beans.
 * Safe for concurrent use by multiple threads.
 *
 * @see Refreshable
 * @see RefreshResult
 * @see AuditLogService
 */
public abstract class AbstractRefreshController {

    private static final Logger log =
            LoggerFactory.getLogger(AbstractRefreshController.class);

    private final AuditLogService auditLogService;

    /**
     * Constructor for subclasses — receives {@link AuditLogService} from Spring.
     *
     * @param auditLogService used to write SUCCESS / FAILURE audit entries — never null
     */
    protected AbstractRefreshController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
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
     * @param authentication Spring Security principal — never null (enforced by filter)
     * @param request        inbound HTTP request for client IP resolution
     * @return {@code 200 OK} with {@link RefreshResult} on success;
     *         exceptions re-thrown to global {@code @ControllerAdvice}
     */
    @PostMapping("/refresh")
    public final ResponseEntity<RefreshResult> refresh(
            Authentication authentication,
            HttpServletRequest request) {

        Refreshable service    = refreshableService();
        String      username   = authentication.getName();
        String      ip         = resolveClientIp(request);
        String      action     = service.getAuditAction();   // e.g. "APP_CONFIG_REFRESH"
        String      resourceId = service.getResourceId();    // e.g. "app_config"
        // resourceId + action captured before refresh() is called —
        // safe to use in catch block even if refresh() throws

        log.info("[{}] refresh requested — user='{}' ip='{}'",
                 action, username, ip);

        try {
            RefreshResult result = service.refresh(username);

            auditLogService.logSuccess(
                    username,
                    action,
                    resourceId,
                    result.count() + " item(s) loaded into " + result.serviceName(),
                    ip
            );

            log.info("[{}] refresh complete — count={} by='{}' ip='{}'",
                     action, result.count(), username, ip);

            return ResponseEntity.ok(result);

        } catch (Exception ex) {

            auditLogService.logFailure(
                    username,
                    action,
                    resourceId,
                    "Refresh failed: " + ex.getMessage(),
                    ip
            );

            log.error("[{}] refresh FAILED — user='{}' reason='{}'",
                      action, username, ex.getMessage(), ex);
            throw ex;
        }
    }

    // =========================================================================
    // Private helper — defined once, not duplicated in any subclass
    // =========================================================================

    /**
     * Resolves the real client IP address.
     *
     * <p>Checks {@code X-Forwarded-For} first (set by reverse proxies /
     * load balancers — first entry is the real client). Falls back to
     * {@link HttpServletRequest#getRemoteAddr()} for direct connections.
     *
     * @param request the inbound HTTP request
     * @return resolved client IP — never null
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
