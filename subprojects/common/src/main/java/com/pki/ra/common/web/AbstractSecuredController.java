package com.pki.ra.common.web;

import com.pki.ra.common.user.service.UserLookupService;
import com.pki.ra.common.util.AuditLogService;
import com.pki.ra.common.util.IpAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Common base class for all PKI-RA admin controllers that perform audit logging.
 *
 * <h3>What this eliminates</h3>
 * Every audited controller used to duplicate three private methods:
 * <ul>
 *   <li>{@code resolveUsername()} — identical in {@link AbstractRefreshController}
 *       and {@link AbstractUserController}</li>
 *   <li>{@code resolveUserId(String)} — same logic, different service in each controller</li>
 *   <li>{@code resolveClientIp(HttpServletRequest)} — byte-for-byte identical everywhere</li>
 * </ul>
 * All three live here now. Controllers call {@link #resolveAuditContext(HttpServletRequest)}
 * to get all three values in one shot as an {@link AuditContext} record.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Holds {@link AuditLogService} and {@link UserLookupService} — the two services
 *       every audited controller needs.</li>
 *   <li>Subclasses inject additional services (e.g. {@link com.pki.ra.common.user.UserManagementService})
 *       via their own constructors.</li>
 *   <li>The three resolution helpers are {@code protected} — visible to subclasses,
 *       invisible outside the controller hierarchy.</li>
 * </ul>
 *
 * <h3>Why SecurityContextHolder, not Authentication parameter</h3>
 * Spring MVC's {@code HandlerMethodArgumentResolver} does not reliably inject
 * {@code Authentication} into methods declared on an abstract class — argument
 * resolution targets the concrete {@code @RestController}, not its parents.
 * {@link SecurityContextHolder} is always populated by the Spring Security filter
 * chain before any controller method executes, regardless of class hierarchy.
 *
 * <h3>Hierarchy</h3>
 * <pre>
 *   AbstractSecuredController
 *   ├── AbstractRefreshController   — POST /refresh for any hot-reloadable cache
 *   └── AbstractUserController      — full user management CRUD endpoints
 * </pre>
 *
 * @see AuditContext
 * @see AbstractRefreshController
 * @see AbstractUserController
 * @author pki-ra
 * @since  1.0.0
 */
public abstract class AbstractSecuredController {

    private static final Logger log =
            LoggerFactory.getLogger(AbstractSecuredController.class);

    /** Writes SUCCESS / FAILURE audit entries — available to all subclasses. */
    protected final AuditLogService  auditLogService;

    /** Resolves numeric userId from authenticated username — available to all subclasses. */
    protected final UserLookupService userLookupService;

    /**
     * Constructor for subclasses.
     *
     * @param auditLogService   writes audit log entries — never null
     * @param userLookupService resolves numeric userId from username — never null
     */
    protected AbstractSecuredController(AuditLogService auditLogService,
                                        UserLookupService userLookupService) {
        this.auditLogService   = auditLogService;
        this.userLookupService = userLookupService;
    }

    // =========================================================================
    // Audit context — one-shot resolution for all three values
    // =========================================================================

    /**
     * Resolves the three audit-log inputs — username, userId, and client IP —
     * from the current security context and incoming request in a single call.
     *
     * <p>Guarantees that the {@code userId} stored in the audit log row and the
     * {@code userId} printed in the SLF4J statement are always identical
     * (resolved once, passed to both).
     *
     * @param request the inbound HTTP request for IP resolution
     * @return populated {@link AuditContext} — never null
     */
    protected AuditContext resolveAuditContext(HttpServletRequest request) {
        String username = resolveUsername();
        Long   userId   = resolveUserId(username);
        String ip       = resolveClientIp(request);
        return AuditContext.of(username, userId, ip);
    }

    // =========================================================================
    // Individual helpers — accessible to subclasses when needed separately
    // =========================================================================

    /**
     * Resolves the authenticated username from {@link SecurityContextHolder}.
     *
     * <p>Returns {@code "anonymous"} as a safe fallback when no authenticated
     * principal is present (misconfiguration or non-production context).
     * In production the admin security filter chain guarantees a non-null,
     * authenticated principal for every {@code /api/admin/**} request.
     *
     * @return username string — never null
     */
    protected String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        log.warn("No authenticated principal in SecurityContext — using 'anonymous'");
        return "anonymous";
    }

    /**
     * Resolves the numeric {@code userId} for the given username via
     * {@link UserLookupService}.
     *
     * <p>Null-safe — returns {@code null} gracefully when:
     * <ul>
     *   <li>The username is {@code "anonymous"} (unauthenticated context).</li>
     *   <li>The user is not yet persisted in the {@code users} table.</li>
     *   <li>The {@code users} table does not exist (backward-compatible).</li>
     * </ul>
     *
     * @param username authenticated username to look up
     * @return numeric userId, or {@code null}
     */
    protected Long resolveUserId(String username) {
        return userLookupService.resolveUserId(username).orElse(null);
    }

    /**
     * Resolves the real client IP address, honouring {@code X-Forwarded-For}
     * headers and normalising the IPv6 loopback ({@code ::1}) to {@code "127.0.0.1"}.
     *
     * @param request the inbound HTTP request
     * @return resolved client IP — never null
     */
    protected String resolveClientIp(HttpServletRequest request) {
        return IpAddressResolver.resolve(request);
    }
}
