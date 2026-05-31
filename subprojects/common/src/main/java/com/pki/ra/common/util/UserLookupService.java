package com.pki.ra.common.util;

import java.util.Optional;

/**
 * Contract for resolving a numeric {@code userId} from an AD username string.
 *
 * <h3>Purpose</h3>
 * Decouples the <em>what</em> (resolve a userId) from the <em>how</em>
 * (JPA Repository lookup, cache, mock, etc.). Every caller depends only on
 * this interface — never on a concrete implementation.
 *
 * <h3>Callers</h3>
 * <ul>
 *   <li>{@link AuditLogService} — enriches {@code audit_log.user_id} on every
 *       log write so audit entries are linked to the users table.</li>
 *   <li>{@link com.pki.ra.common.web.AbstractRefreshController} — resolves
 *       {@code userId} for structured logging on every refresh request.</li>
 * </ul>
 *
 * <h3>Contract</h3>
 * Implementations <strong>must never throw</strong> — every failure path must
 * return {@link Optional#empty()}. The audit log must not be blocked by a
 * userId resolution failure.
 *
 * <h3>Known implementations</h3>
 * <ul>
 *   <li>{@link UserLookupServiceImpl} — production: JPA Repository lookup
 *       against the {@code users} table.</li>
 * </ul>
 *
 * @author pki-ra
 * @since  1.0.0
 * @see UserLookupServiceImpl
 * @see AuditLogService
 */
public interface UserLookupService {

    /**
     * Resolves the numeric {@code userId} for the given AD username.
     *
     * <h3>Guaranteed behaviour</h3>
     * <ul>
     *   <li>Never throws — all failure paths return {@link Optional#empty()}.</li>
     *   <li>{@code "system"} / {@code "anonymous"} → {@link Optional#empty()}
     *       without any DB call.</li>
     *   <li>Username not found in DB → {@link Optional#empty()}.</li>
     * </ul>
     *
     * @param username AD sAMAccountName (e.g. {@code "john.doe"}) — may be null
     * @return the user's numeric ID, or {@link Optional#empty()} on any fallback
     */
    Optional<Long> resolveUserId(String username);
}
