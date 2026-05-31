package com.pki.ra.common.util;

import com.pki.ra.common.model.AuditLog;
import com.pki.ra.common.user.UserManagementService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-only service for persisting {@link AuditLog} entries.
 *
 * <p>Uses {@link Propagation#REQUIRES_NEW} so audit records are always
 * committed independently of the calling transaction — even if the
 * caller rolls back, the audit trail is preserved.
 *
 * <h3>Self-invocation fix</h3>
 * Spring AOP cannot intercept {@code this.method()} calls within the same bean
 * (the proxy is bypassed). Therefore:
 * <ul>
 *   <li>Every <em>public</em> method carries {@code @Transactional(REQUIRES_NEW)}
 *       and delegates to the <em>private</em> {@link #persistEntry} method.</li>
 *   <li>{@code persistEntry} is <strong>not</strong> annotated — it relies on
 *       the transaction already opened by its public caller.</li>
 *   <li>This guarantees exactly one {@code REQUIRES_NEW} transaction per call,
 *       regardless of which public method is used.</li>
 * </ul>
 *
 * <h3>userId resolution — single vs double DB call</h3>
 * Two overload families are provided for {@code logSuccess} and {@code logFailure}:
 * <ul>
 *   <li><b>Without {@code userId}</b> — callers that do not already hold a resolved
 *       userId. {@link #persistEntry} resolves it internally via
 *       {@link UserManagementService#resolveUserId(String)}.</li>
 *   <li><b>With {@code userId}</b> — callers that have <em>already</em> resolved the
 *       userId (e.g. {@link com.pki.ra.common.web.AbstractUserController}).
 *       Passing it here skips the second DB call and guarantees the logged value
 *       and the stored value are identical.</li>
 * </ul>
 *
 * <p><b>jSpecify:</b> {@code resourceId}, {@code description}, and {@code ipAddress}
 * are {@link Nullable} — batch/scheduled jobs have no HTTP request context.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Slf4j
@Service
public class AuditLogService {

    // =========================================================================
    // Outcome constants — single source of truth, eliminates magic strings
    // =========================================================================

    /** Outcome value written when an action completes successfully. */
    public static final String OUTCOME_SUCCESS = "SUCCESS";

    /** Outcome value written when an action fails for any reason. */
    public static final String OUTCOME_FAILURE = "FAILURE";

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Injected lazily to break the potential Spring circular-dependency cycle:
     * {@code AuditLogService} ← {@code UserManagementService} ← repositories.
     * {@code @Lazy} defers proxy creation until first method call.
     */
    private final UserManagementService userManagementService;

    public AuditLogService(@Lazy UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    // =========================================================================
    // log — generic, caller-supplied outcome
    // =========================================================================

    /**
     * Records an audit entry with a caller-supplied outcome.
     * userId is resolved internally — use when the caller does not already hold it.
     *
     * @param username    AD sAMAccountName of the acting user — never null
     * @param action      action-type constant, e.g. {@code USER_CREATE}
     * @param resourceId  optional resource the action targeted
     * @param description human-readable summary of what happened
     * @param ipAddress   originating client IP — {@code null} for batch/scheduler jobs
     * @param outcome     {@code "SUCCESS"} or {@code "FAILURE"}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String username,
                    String action,
                    @Nullable String resourceId,
                    @Nullable String description,
                    @Nullable String ipAddress,
                    String outcome) {

        persistEntry(username, action, resourceId, description, ipAddress, outcome, null);
    }

    // =========================================================================
    // logSuccess — two overloads
    // =========================================================================

    /**
     * Records a successful action — outcome fixed to {@code "SUCCESS"}.
     * userId is resolved internally via {@link UserManagementService}.
     * Use this when the caller does not already hold a resolved userId.
     *
     * @param username    AD username
     * @param action      action-type constant
     * @param resourceId  optional resource identifier
     * @param description human-readable summary
     * @param ipAddress   originating IP ({@code null} for batch jobs)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(String username,
                           String action,
                           @Nullable String resourceId,
                           @Nullable String description,
                           @Nullable String ipAddress) {

        persistEntry(username, action, resourceId, description, ipAddress, OUTCOME_SUCCESS, null);
    }

    /**
     * Records a successful action — outcome fixed to {@code "SUCCESS"}.
     * Accepts a pre-resolved {@code userId} — skips the internal DB lookup,
     * avoiding a redundant {@link UserManagementService#resolveUserId(String)} call.
     *
     * <p>Use this overload when the caller has <em>already</em> resolved the userId
     * (e.g. {@link com.pki.ra.common.web.AbstractUserController}) so that the value
     * logged in SLF4J and the value written to the DB are guaranteed identical.
     *
     * @param username    AD username
     * @param action      action-type constant
     * @param resourceId  optional resource identifier
     * @param description human-readable summary
     * @param ipAddress   originating IP ({@code null} for batch jobs)
     * @param userId      pre-resolved numeric user ID — {@code null} if unknown
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(String username,
                           String action,
                           @Nullable String resourceId,
                           @Nullable String description,
                           @Nullable String ipAddress,
                           @Nullable Long userId) {

        persistEntry(username, action, resourceId, description, ipAddress, OUTCOME_SUCCESS, userId);
    }

    // =========================================================================
    // logFailure — two overloads
    // =========================================================================

    /**
     * Records a failed action — outcome fixed to {@code "FAILURE"}.
     * userId is resolved internally via {@link UserManagementService}.
     * Use this when the caller does not already hold a resolved userId.
     *
     * @param username    AD username
     * @param action      action-type constant
     * @param resourceId  optional resource identifier
     * @param description failure reason or exception message
     * @param ipAddress   originating IP ({@code null} for batch jobs)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String username,
                           String action,
                           @Nullable String resourceId,
                           @Nullable String description,
                           @Nullable String ipAddress) {

        persistEntry(username, action, resourceId, description, ipAddress, OUTCOME_FAILURE, null);
    }

    /**
     * Records a failed action — outcome fixed to {@code "FAILURE"}.
     * Accepts a pre-resolved {@code userId} — skips the internal DB lookup.
     *
     * <p>Use this overload when the caller has <em>already</em> resolved the userId
     * (e.g. {@link com.pki.ra.common.web.AbstractUserController}) so that the value
     * logged in SLF4J and the value written to the DB are identical.
     *
     * @param username    AD username
     * @param action      action-type constant
     * @param resourceId  optional resource identifier
     * @param description failure reason or exception message
     * @param ipAddress   originating IP ({@code null} for batch jobs)
     * @param userId      pre-resolved numeric user ID — {@code null} if unknown
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String username,
                           String action,
                           @Nullable String resourceId,
                           @Nullable String description,
                           @Nullable String ipAddress,
                           @Nullable Long userId) {

        persistEntry(username, action, resourceId, description, ipAddress, OUTCOME_FAILURE, userId);
    }

    // =========================================================================
    // Private helper — NO @Transactional (relies on the caller's REQUIRES_NEW)
    // =========================================================================

    /**
     * Builds and saves one {@link AuditLog} entry.
     *
     * <p><strong>Not annotated with {@code @Transactional}</strong> by design.
     * Spring AOP cannot intercept same-bean {@code this.x()} calls (self-invocation
     * bypasses the proxy). Putting {@code REQUIRES_NEW} here would silently
     * <em>not</em> create a new transaction when called from a public sibling method.
     *
     * <p>The correct pattern: each <em>public</em> method owns one
     * {@code REQUIRES_NEW} transaction and delegates here.
     *
     * <h3>userId resolution strategy</h3>
     * <ul>
     *   <li>If {@code userId} is non-null — use it directly. No DB call.
     *       Caller already resolved it (e.g. {@link com.pki.ra.common.web.AbstractUserController}).</li>
     *   <li>If {@code userId} is {@code null} — resolve via
     *       {@link UserManagementService#resolveUserId(String)}, which is null-safe
     *       and never throws.</li>
     * </ul>
     *
     * @param username    AD sAMAccountName
     * @param action      action-type constant
     * @param resourceId  nullable resource identifier
     * @param description nullable human-readable summary
     * @param ipAddress   nullable client IP
     * @param outcome     {@code "SUCCESS"} or {@code "FAILURE"}
     * @param userId      pre-resolved userId, or {@code null} to trigger internal resolution
     */
    private void persistEntry(String username,
                              String action,
                              @Nullable String resourceId,
                              @Nullable String description,
                              @Nullable String ipAddress,
                              String outcome,
                              @Nullable Long userId) {

        // Use pre-resolved userId if provided; otherwise resolve now (one DB call max)
        Long resolvedUserId = (userId != null)
                ? userId
                : userManagementService.resolveUserId(username).orElse(null);

        AuditLog entry = AuditLog.builder()
                .username(username)
                .userId(resolvedUserId)
                .action(action)
                .resourceId(resourceId)
                .description(description)
                .ipAddress(ipAddress)
                .outcome(outcome)
                .build();

        entityManager.persist(entry);

        log.debug("Audit  user={}  userId={}  action={}  resource={}  outcome={}  ip={}",
                  username, resolvedUserId, action, resourceId, outcome, ipAddress);
    }
}
