package com.pki.ra.common.util;

import com.pki.ra.common.model.AuditLog;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-only service for persisting {@link AuditLog} entries.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Build and save {@link AuditLog} rows via {@link AuditLogRepository}.</li>
 *   <li>Each public method carries {@link Propagation#REQUIRES_NEW} so audit
 *       entries are committed in their own transaction — independent of the
 *       caller's transaction outcome (rollback does not erase the audit row).</li>
 * </ul>
 *
 * <h3>Self-invocation fix</h3>
 * Spring AOP cannot intercept {@code this.method()} calls within the same bean
 * (the proxy is bypassed). Therefore:
 * <ul>
 *   <li>Every <em>public</em> method carries {@code @Transactional(REQUIRES_NEW)}
 *       and delegates to the <em>private</em> {@link #persistEntry} method.</li>
 *   <li>{@code persistEntry} is <strong>not</strong> annotated — it relies on
 *       the transaction already opened by its public caller.</li>
 *   <li>This guarantees that exactly one {@code REQUIRES_NEW} transaction is
 *       created per call, regardless of which public method is used.</li>
 * </ul>
 *
 * <h3>Read / query path</h3>
 * Inject {@link AuditLogRepository} directly — it provides paginated, filtered
 * and aggregated queries without going through this service.
 *
 * <h3>jSpecify nullability</h3>
 * {@code resourceId}, {@code description}, and {@code ipAddress} are
 * {@link Nullable} — batch / scheduled jobs have no HTTP request context.
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

    private final AuditLogRepository auditLogRepository;
    private final UserLookupService  userLookupService;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           UserLookupService userLookupService) {
        this.auditLogRepository = auditLogRepository;
        this.userLookupService  = userLookupService;
    }

    // =========================================================================
    // Public API — each method carries REQUIRES_NEW
    // =========================================================================

    /**
     * Records one audit entry with a caller-supplied outcome.
     *
     * <p>Opens a brand-new transaction ({@link Propagation#REQUIRES_NEW}),
     * builds the {@link AuditLog} via the builder, saves it through
     * {@link AuditLogRepository#save}, then commits — all independently
     * of any surrounding transaction.
     *
     * @param username    AD sAMAccountName of the acting user — never null
     * @param action      action-type constant, e.g. {@code CONFIG_REFRESH}
     * @param resourceId  optional resource the action targeted (table name, serial, etc.)
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

        persistEntry(username, action, resourceId, description, ipAddress, outcome);
    }

    /**
     * Records a successful action — outcome fixed to {@code "SUCCESS"}.
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

        persistEntry(username, action, resourceId, description, ipAddress, OUTCOME_SUCCESS);
    }

    /**
     * Records a failed action — outcome fixed to {@code "FAILURE"}.
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

        persistEntry(username, action, resourceId, description, ipAddress, OUTCOME_FAILURE);
    }

    /**
     * Records a system/batch action with no HTTP context.
     *
     * <p>{@code ipAddress} is set to {@code null} automatically — batch jobs and
     * scheduler tasks run without an inbound HTTP request.
     *
     * <p>Example:
     * <pre>{@code
     * auditLogService.logSystem(
     *     "system", "CERT_EXPIRY_SCAN", "cert-expiry-job",
     *     "Scanned 1024 certs — 3 expiring within 30 days");
     * }</pre>
     *
     * @param username    typically {@code "system"} or the scheduler job name
     * @param action      action-type constant
     * @param resourceId  optional resource identifier
     * @param description human-readable summary
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSystem(String username,
                          String action,
                          @Nullable String resourceId,
                          @Nullable String description) {

        persistEntry(username, action, resourceId, description, null, OUTCOME_SUCCESS);
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
     * @param username    AD sAMAccountName
     * @param action      action-type constant
     * @param resourceId  nullable resource identifier
     * @param description nullable human-readable summary
     * @param ipAddress   nullable client IP
     * @param outcome     {@code "SUCCESS"} or {@code "FAILURE"}
     */
    private void persistEntry(String username,
                              String action,
                              @Nullable String resourceId,
                              @Nullable String description,
                              @Nullable String ipAddress,
                              String outcome) {

        // Resolve userId — null-safe, never throws (see UserLookupService javadoc)
        Long userId = userLookupService.resolveUserId(username).orElse(null);

        AuditLog entry = AuditLog.builder()
                .username(username)
                .userId(userId)
                .action(action)
                .resourceId(resourceId)
                .description(description)
                .ipAddress(ipAddress)
                .outcome(outcome)
                .build();

        auditLogRepository.save(entry);

        log.debug("Audit  user={}  userId={}  action={}  resource={}  outcome={}  ip={}",
                  username, userId, action, resourceId, outcome, ipAddress);
    }
}
