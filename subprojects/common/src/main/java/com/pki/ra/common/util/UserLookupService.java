package com.pki.ra.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Lightweight service that resolves a numeric {@code userId} from an AD username string.
 *
 * <h3>Why JdbcTemplate — not JPA?</h3>
 * The {@code User} JPA entity lives in the {@code user-management} branch which is
 * not yet merged into this branch. Using {@link JdbcTemplate} with a native SQL query
 * lets us query the {@code users} table directly without requiring the entity class
 * to exist on this branch.
 *
 * <h3>Backward compatibility — graceful degradation</h3>
 * Every failure path returns {@link Optional#empty()} instead of throwing:
 * <ul>
 *   <li>If the {@code users} table does not exist yet — {@code DataAccessException}
 *       is caught → returns {@code empty()}.</li>
 *   <li>If the username has no matching row — empty result list → returns
 *       {@code empty()}.</li>
 *   <li>{@code "system"} and {@code "anonymous"} — short-circuited before any
 *       DB call → returns {@code empty()}.</li>
 * </ul>
 * In all fallback cases {@code audit_log.user_id} is stored as {@code NULL} —
 * the audit entry is always written successfully.
 *
 * <h3>Future migration path</h3>
 * Once the user-management branch is merged:
 * <ol>
 *   <li>This service can be replaced by
 *       {@code UserManagementService#resolveUserId(username)}.</li>
 *   <li>The {@code users} table FK constraint on {@code audit_log.user_id}
 *       can be added via a new Flyway migration.</li>
 * </ol>
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Slf4j
@Service
public class UserLookupService {

    /** System-level actors that have no row in the users table. */
    private static final String SYSTEM_USER    = "system";
    private static final String ANONYMOUS_USER = "anonymous";
    private static final String ANONYMOUS_FULL = "anonymousUser";

    private static final String SQL_FIND_USER_ID =
            "SELECT id FROM users WHERE username = ? LIMIT 1";

    private final JdbcTemplate jdbcTemplate;

    public UserLookupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Resolves the numeric {@code userId} for the given AD username.
     *
     * <p>Called by {@link AuditLogService} on every audit log write to enrich
     * {@code audit_log.user_id} — enabling user-scoped queries later.
     *
     * <p>Always returns safely — never throws. See class-level Javadoc for
     * the full fallback strategy.
     *
     * @param username AD sAMAccountName (e.g. {@code "john.doe"})
     * @return the user's numeric ID, or {@link Optional#empty()} on any fallback
     */
    public Optional<Long> resolveUserId(String username) {

        // ── 1. Short-circuit: system / anonymous actors have no users-table row ──
        if (isSystemActor(username)) {
            return Optional.empty();
        }

        // ── 2. Query users table — graceful fallback if table is absent ──
        try {
            List<Long> rows = jdbcTemplate.query(
                    SQL_FIND_USER_ID,
                    (rs, rowNum) -> rs.getLong("id"),
                    username
            );
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));

        } catch (Exception ex) {
            /*
             * DataAccessException thrown when:
             *   - users table does not exist (user-management not yet deployed)
             *   - DB connectivity issue
             * Logged at TRACE — not an error, expected during migration period.
             */
            log.trace("userId lookup skipped for '{}' — users table unavailable: {}",
                      username, ex.getMessage());
            return Optional.empty();
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private boolean isSystemActor(String username) {
        return username == null
                || SYSTEM_USER.equals(username)
                || ANONYMOUS_USER.equals(username)
                || ANONYMOUS_FULL.equals(username);
    }
}
