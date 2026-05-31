package com.pki.ra.common.util;

import com.pki.ra.common.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service that resolves a numeric {@code userId} from an AD username string,
 * using JPA Repository for type-safe, Spring Data–managed DB access.
 *
 * <h3>Why JPA Repository — not JdbcTemplate?</h3>
 * <ul>
 *   <li>Type-safe — queries return {@link User} entities, not raw {@code ResultSet}.</li>
 *   <li>Spring-managed — transactions, connection pooling, and exception translation
 *       all handled by the Spring Data JPA infrastructure.</li>
 *   <li>Readable — {@code findByUsernameAndIsActiveTrue(username)} expresses intent
 *       clearly; no SQL string literals to maintain.</li>
 *   <li>Testable — {@link UserLookupRepository} can be mocked in unit tests
 *       without spinning up a {@code JdbcTemplate} or a real DataSource.</li>
 * </ul>
 *
 * <h3>Backward compatibility — graceful degradation</h3>
 * Every failure path returns {@link Optional#empty()} instead of throwing:
 * <ul>
 *   <li>{@code "system"} / {@code "anonymous"} — short-circuited before any
 *       DB call — these actors have no row in the {@code users} table.</li>
 *   <li>Username not found in {@code users} table — repository returns empty
 *       → {@link Optional#empty()} propagated.</li>
 *   <li>Any unexpected exception from the repository layer — caught and logged
 *       at {@code TRACE}; audit entry is still written with {@code user_id = NULL}.</li>
 * </ul>
 * The audit log is <strong>never blocked</strong> by a userId resolution failure.
 *
 * @author pki-ra
 * @since  1.0.0
 * @see UserLookupRepository
 * @see AuditLogService
 */
@Slf4j
@Service
public class UserLookupService {

    // -------------------------------------------------------------------------
    // System-level actors that have no row in the users table
    // -------------------------------------------------------------------------
    private static final String SYSTEM_USER    = "system";
    private static final String ANONYMOUS_USER = "anonymous";
    private static final String ANONYMOUS_FULL = "anonymousUser";

    private final UserLookupRepository userLookupRepository;

    public UserLookupService(UserLookupRepository userLookupRepository) {
        this.userLookupRepository = userLookupRepository;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Resolves the numeric {@code userId} for the given AD username.
     *
     * <p>Called by {@link AuditLogService} on every audit log write to enrich
     * {@code audit_log.user_id}, enabling fast user-scoped queries later.
     *
     * <p>Read-only transaction — no locks, no writes, minimal DB overhead.
     *
     * <h3>Execution path</h3>
     * <pre>
     *  isSystemActor(username)
     *      → true  : return empty()         (no DB call)
     *      → false : userLookupRepository.findByUsernameAndIsActiveTrue(username)
     *                    → found  : return Optional.of(user.getId())
     *                    → absent : return empty()
     *                    → throws : log TRACE + return empty()
     * </pre>
     *
     * @param username AD sAMAccountName, e.g. {@code "john.doe"}
     * @return numeric userId wrapped in {@link Optional},
     *         or {@link Optional#empty()} on any fallback — never throws
     */
    @Transactional(readOnly = true)
    public Optional<Long> resolveUserId(String username) {

        // ── Step 1: Short-circuit for system / anonymous actors ──────────────
        if (isSystemActor(username)) {
            log.trace("resolveUserId: skipping system actor '{}'", username);
            return Optional.empty();
        }

        // ── Step 2: JPA lookup — active users only ───────────────────────────
        try {
            Optional<Long> userId = userLookupRepository
                    .findByUsernameAndIsActiveTrue(username)
                    .map(User::getId);

            if (userId.isEmpty()) {
                log.trace("resolveUserId: no active user found for username='{}'", username);
            }

            return userId;

        } catch (Exception ex) {
            /*
             * Catches any DataAccessException or JPA infrastructure exception.
             * Logged at TRACE — this is expected during the migration period
             * before the users table is created.
             * Audit entry is still written with user_id = NULL.
             */
            log.trace("resolveUserId: lookup failed for '{}' — {}",
                      username, ex.getMessage());
            return Optional.empty();
        }
    }

    // =========================================================================
    // Private helper
    // =========================================================================

    /**
     * Returns {@code true} for well-known system-level actors that have no row
     * in the {@code users} table and should never trigger a DB lookup.
     *
     * @param username the actor username — may be {@code null}
     * @return {@code true} if the actor is a system/anonymous principal
     */
    private boolean isSystemActor(String username) {
        return username == null
                || SYSTEM_USER.equals(username)
                || ANONYMOUS_USER.equals(username)
                || ANONYMOUS_FULL.equals(username);
    }
}
