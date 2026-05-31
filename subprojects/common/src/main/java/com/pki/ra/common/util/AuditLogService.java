package com.pki.ra.common.util;

import com.pki.ra.common.model.AuditLog;
import com.pki.ra.common.user.service.UserLookupService;
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
 * <h3>userId resolution — single vs double DB call</h3>
 * Two overload families are provided for {@code logSuccess} and {@code logFailure}:
 * <ul>
 *   <li><b>Without userId</b> — callers that do not already hold a resolved
 *       userId. {@link #persistEntry} resolves it internally via
 *       {@link UserLookupService#resolveUserId(String)}.</li>
 *   <li><b>With userId</b> — callers that have already resolved the userId
 *       (e.g. {@link com.pki.ra.common.web.AbstractRefreshController},
 *       {@link com.pki.ra.common.web.AbstractUserController}).
 *       Passing it skips the second DB call and guarantees log and DB values
 *       are identical.</li>
 * </ul>
 *
 * <h3>Read / query path</h3>
 * Inject {@link AuditLogRepository} directly for paginated and filtered queries.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Slf4j
@Service
public class AuditLogService {

    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";

    private final AuditLogRepository auditLogRepository;
    private final UserLookupService  userLookupService;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           UserLookupService userLookupService) {
        this.auditLogRepository = auditLogRepository;
        this.userLookupService  = userLookupService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String username, String action,
                    @Nullable String resourceId, @Nullable String description,
                    @Nullable String ipAddress, String outcome) {
        persistEntry(username, action, resourceId, description, ipAddress, outcome, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(String username, String action,
                           @Nullable String resourceId, @Nullable String description,
                           @Nullable String ipAddress) {
        persistEntry(username, action, resourceId, description, ipAddress, OUTCOME_SUCCESS, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(String username, String action,
                           @Nullable String resourceId, @Nullable String description,
                           @Nullable String ipAddress, @Nullable Long userId) {
        persistEntry(username, action, resourceId, description, ipAddress, OUTCOME_SUCCESS, userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String username, String action,
                           @Nullable String resourceId, @Nullable String description,
                           @Nullable String ipAddress) {
        persistEntry(username, action, resourceId, description, ipAddress, OUTCOME_FAILURE, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String username, String action,
                           @Nullable String resourceId, @Nullable String description,
                           @Nullable String ipAddress, @Nullable Long userId) {
        persistEntry(username, action, resourceId, description, ipAddress, OUTCOME_FAILURE, userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSystem(String username, String action,
                          @Nullable String resourceId, @Nullable String description) {
        persistEntry(username, action, resourceId, description, null, OUTCOME_SUCCESS, null);
    }

    private void persistEntry(String username, String action,
                              @Nullable String resourceId, @Nullable String description,
                              @Nullable String ipAddress, String outcome,
                              @Nullable Long userId) {

        Long resolvedUserId = (userId != null)
                ? userId
                : userLookupService.resolveUserId(username).orElse(null);

        AuditLog entry = AuditLog.builder()
                .username(username)
                .userId(resolvedUserId)
                .action(action)
                .resourceId(resourceId)
                .description(description)
                .ipAddress(ipAddress)
                .outcome(outcome)
                .build();

        auditLogRepository.save(entry);

        log.debug("Audit  user={}  userId={}  action={}  resource={}  outcome={}  ip={}",
                  username, resolvedUserId, action, resourceId, outcome, ipAddress);
    }
}
