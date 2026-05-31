package com.pki.ra.common.web;

import com.pki.ra.common.config.Refreshable;
import com.pki.ra.common.config.dto.RefreshResult;
import com.pki.ra.common.user.service.UserLookupService;
import com.pki.ra.common.util.AuditLogService;
import com.pki.ra.common.util.IpAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Abstract base controller providing a complete {@code POST /refresh} endpoint
 * for any hot-reloadable cache — defined once, reused by every module.
 *
 * <h3>Design Pattern — Template Method</h3>
 * Full refresh flow defined here once:
 * <ol>
 *   <li>Extract {@code username} + client IP from the request.</li>
 *   <li>Resolve numeric {@code userId} from {@code username} via
 *       {@link UserLookupService} — backward-compatible, never throws.</li>
 *   <li>Read service metadata ({@code auditAction}, {@code resourceId})
 *       from the {@link Refreshable} bean.</li>
 *   <li>Call {@link Refreshable#refresh(String)}.</li>
 *   <li>Write {@code SUCCESS} audit entry (with {@code userId}) on success.</li>
 *   <li>Return {@code 200 OK} with {@link RefreshResult}.</li>
 *   <li>On exception: write {@code FAILURE} audit entry, log error, re-throw.</li>
 * </ol>
 * Subclasses supply only the {@link Refreshable} bean via
 * {@link #refreshableService()} — all steps are inherited.
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
 * <h3>userId in logs — why and how</h3>
 * {@link UserLookupService#resolveUserId(String)} performs a lightweight
 * native SQL query ({@code SELECT id FROM users WHERE username = ?}).
 * The resolved {@code userId} is logged in every SLF4J statement so that
 * log aggregation tools (Grafana Loki, Splunk, ELK) can filter by numeric ID
 * instead of potentially changing username strings.
 * Returns {@code null} gracefully if the {@code users} table is absent
 * (backward compatible) — logs show {@code userId=null} instead of failing.
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
 * @see UserLookupService
 */
public abstract class AbstractRefreshController {

    private static final Logger log =
            LoggerFactory.getLogger(AbstractRefreshController.class);

    private final AuditLogService  auditLogService;
    private final UserLookupService userLookupService;

    /**
     * Constructor for subclasses — receives both services from Spring.
     *
     * @param auditLogService   writes SUCCESS / FAILURE audit entries — never null
     * @param userLookupService resolves numeric userId from AD username — never null
     */
    protected AbstractRefreshController(AuditLogService auditLogService,
                                        UserLookupService userLookupService) {
        this.auditLogService   = auditLogService;
        this.userLookupService = userLookupService;
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
     * <h3>Flow</h3>
     * <pre>
     *  SecurityContextHolder → username
     *  UserLookupService     → userId  (null-safe, backward-compatible)
     *  IpAddressResolver     → ip      (X-Forwarded-For + IPv6 normalised)
     *  Refreshable           → refresh()
     *  AuditLogService       → logSuccess / logFailure
     * </pre>
     *
     * <h3>Why SecurityContextHolder instead of Authentication parameter?</h3>
     * Spring MVC's {@code HandlerMethodArgumentResolver} does not reliably
     * inject {@code Authentication} into methods declared on an abstract class —
     * argument resolution targets the concrete {@code @RestController} class,
     * not its inherited methods. Reading directly from
     * {@link SecurityContextHolder} always works because it is a thread-local
     * populated by Spring Security's filter chain before any controller method
     * is invoked, regardless of class hierarchy.
     *
     * @param request inbound HTTP request — for client IP resolution only
     * @return {@code 200 OK} with {@link RefreshResult} on success;
     *         exceptions re-thrown to global {@code @ControllerAdvice}
     */
    @PostMapping("/refresh")
    public final ResponseEntity<RefreshResult> refresh(HttpServletRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Refreshable service    = refreshableService();
        String      username   = resolveUsername(authentication);
        Long        userId     = userLookupService.resolveUserId(username).orElse(null);
        String      ip         = resolveClientIp(request);
        String      action     = service.getAuditAction();   // e.g. "APP_CONFIG_REFRESH"
        String      resourceId = service.getResourceId();    // e.g. "app_config"
        // resourceId + action captured before refresh() is called —
        // safe to use in catch block even if refresh() throws

        log.info("[{}] refresh requested — userId='{}' username='{}' ip='{}'",
                 action, userId, username, ip);

        try {
            RefreshResult result = service.refresh(username);

            auditLogService.logSuccess(
                    username,
                    action,
                    resourceId,
                    result.count() + " item(s) loaded into " + result.serviceName(),
                    ip
            );

            log.info("[{}] refresh complete — count={} userId='{}' username='{}' ip='{}'",
                     action, result.count(), userId, username, ip);

            return ResponseEntity.ok(result);

        } catch (Exception ex) {

            auditLogService.logFailure(
                    username,
                    action,
                    resourceId,
                    "Refresh failed: " + ex.getMessage(),
                    ip
            );

            log.error("[{}] refresh FAILED — userId='{}' username='{}' reason='{}'",
                      action, userId, username, ex.getMessage(), ex);
            throw ex;
        }
    }

    // =========================================================================
    // Private helpers — defined once, not duplicated in any subclass
    // =========================================================================

    /**
     * Resolves the authenticated username from the {@link Authentication} object.
     *
     * <p>Returns {@code "anonymous"} as a safe fallback when:
     * <ul>
     *   <li>The security filter chain did not populate the context (misconfiguration).</li>
     *   <li>The endpoint is called in a context without an active security session.</li>
     * </ul>
     * In production the admin security filter chain guarantees a non-null,
     * authenticated principal for every {@code /api/admin/**} request.
     *
     * @param authentication the {@link Authentication} from {@link SecurityContextHolder},
     *                       may be {@code null}
     * @return username string — never null
     */
    private String resolveUsername(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        log.warn("refresh() called with no authenticated principal — using 'anonymous'");
        return "anonymous";
    }

    /**
     * Resolves the real client IP address, normalising IPv6 loopback to
     * {@code "127.0.0.1"} for readable logs and consistent audit entries.
     *
     * @param request the inbound HTTP request
     * @return resolved client IP — never null
     */
    private String resolveClientIp(HttpServletRequest request) {
        return IpAddressResolver.resolve(request);
    }
}
