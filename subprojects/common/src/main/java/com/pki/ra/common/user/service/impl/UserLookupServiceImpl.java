package com.pki.ra.common.user.service.impl;

import com.pki.ra.common.model.User;
import com.pki.ra.common.user.service.UserLookupService;
import com.pki.ra.common.util.UserLookupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Production implementation of {@link UserLookupService}.
 *
 * <p>Resolves a numeric {@code userId} from an AD username string using
 * {@link UserLookupRepository} — a Spring Data JPA repository backed by
 * the {@code users} table.
 *
 * <h3>Package rationale — {@code com.pki.ra.common.user.service.impl}</h3>
 * <ul>
 *   <li>{@code service.impl} — concrete implementations are hidden from
 *       callers who depend only on the {@code service} interface package.</li>
 *   <li>Spring auto-wires this class wherever {@link UserLookupService}
 *       is injected — callers never import this class directly.</li>
 * </ul>
 *
 * <h3>Why JPA Repository — not JdbcTemplate?</h3>
 * <ul>
 *   <li><b>Type-safe</b> — queries return {@link User} entities, not raw {@code ResultSet}.</li>
 *   <li><b>Spring-managed</b> — transactions, connection pooling, and exception
 *       translation all handled by Spring Data JPA infrastructure.</li>
 *   <li><b>Readable</b> — {@code findByUsernameAndIsActiveTrue(username)} expresses
 *       intent clearly; no SQL string literals to maintain.</li>
 *   <li><b>Testable</b> — {@link UserLookupRepository} is easily mocked;
 *       {@link UserLookupService} interface can be stubbed independently.</li>
 * </ul>
 *
 * <h3>Backward compatibility — graceful degradation</h3>
 * Every failure path returns {@link Optional#empty()} — never throws:
 * <ul>
 *   <li>{@code "system"} / {@code "anonymous"} — short-circuited; no DB call.</li>
 *   <li>Username absent from {@code users} table — repository returns empty.</li>
 *   <li>Any {@code DataAccessException} (e.g. {@code users} table not yet created) —
 *       caught, logged at {@code TRACE}; audit entry still written with
 *       {@code user_id = NULL}.</li>
 * </ul>
 *
 * @author pki-ra
 * @since  1.0.0
 * @see UserLookupService
 * @see UserLookupRepository
 */
@Slf4j
@Service
public class UserLookupServiceImpl implements UserLookupService {

    // -------------------------------------------------------------------------
    // System-level actors — no row in users table, skip DB lookup entirely
    // -------------------------------------------------------------------------
    private static final String SYSTEM_USER    = "system";
    private static final String ANONYMOUS_USER = "anonymous";
    private static final String ANONYMOUS_FULL = "anonymousUser";

    private final UserLookupRepository userLookupRepository;

    public UserLookupServiceImpl(UserLookupRepository userLookupRepository) {
        this.userLookupRepository = userLookupRepository;
    }

    // =========================================================================
    // UserLookupService implementation
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <h3>Execution path</h3>
     * <pre>
     *  isSystemActor(username)
     *      → true  : return empty()                    (no DB call)
     *      → false : userLookupRepository
     *                  .findByUsernameAndIsActiveTrue(username)
     *                      → found  : return Optional.of(user.getId())
     *                      → absent : return empty()
     *                      → throws : log TRACE + return empty()
     * </pre>
     *
     * <p>Read-only transaction — no locks, no writes, minimal DB overhead.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Long> resolveUserId(String username) {

        // ── Step 1: Short-circuit — system / anonymous have no users-table row ──
        if (isSystemActor(username)) {
            log.trace("resolveUserId: skipping system actor '{}'", username);
            return Optional.empty();
        }

        // ── Step 2: JPA lookup — active users only ────────────────────────────
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
             * Catches DataAccessException or any JPA infrastructure exception.
             * Logged at TRACE — expected during migration period before the
             * users table is deployed on this environment.
             * Audit entry is still committed with user_id = NULL.
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
     * Returns {@code true} for well-known system-level actors that have no
     * row in the {@code users} table and must not trigger a DB lookup.
     *
     * @param username actor username — may be {@code null}
     * @return {@code true} if the actor is system / anonymous
     */
    private boolean isSystemActor(String username) {
        return username == null
                || SYSTEM_USER.equals(username)
                || ANONYMOUS_USER.equals(username)
                || ANONYMOUS_FULL.equals(username);
    }
}
