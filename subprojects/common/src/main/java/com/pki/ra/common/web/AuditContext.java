package com.pki.ra.common.web;

/**
 * Immutable value object that bundles the three pieces of context resolved at
 * the start of every audited controller request.
 *
 * <h3>Why a record?</h3>
 * <ul>
 *   <li>Eliminates the three parallel local variables ({@code actor}, {@code actorId},
 *       {@code ip}) repeated in every audited endpoint.</li>
 *   <li>One call to {@link AbstractSecuredController#resolveAuditContext(jakarta.servlet.http.HttpServletRequest)}
 *       replaces three independent calls and makes the resolution atomic from
 *       the caller's perspective.</li>
 *   <li>Passed as a single argument to both the SLF4J log statement and the
 *       {@link com.pki.ra.common.util.AuditLogService} call — no risk of
 *       using a different {@code userId} in the log vs. the DB row.</li>
 * </ul>
 *
 * @param username resolved from {@link org.springframework.security.core.context.SecurityContextHolder};
 *                 falls back to {@code "anonymous"} if no authenticated principal exists
 * @param userId   numeric DB primary key for {@code username} resolved via
 *                 {@link com.pki.ra.common.user.service.UserLookupService};
 *                 {@code null} when the user is anonymous or the lookup fails
 * @param ip       real client IP address (X-Forwarded-For aware, IPv6 loopback normalised
 *                 to {@code "127.0.0.1"})
 *
 * @see AbstractSecuredController
 * @author pki-ra
 * @since  1.0.0
 */
public record AuditContext(String username, Long userId, String ip) {

    /**
     * Convenience factory — used by {@link AbstractSecuredController} only.
     */
    static AuditContext of(String username, Long userId, String ip) {
        return new AuditContext(username, userId, ip);
    }
}
