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
 *   <li>Build and save {@code AuditLog} rows via {@link AuditLogRepository}.</li>
 *   <li>Always runs in its own transaction ({@link Propagation#REQUIRES_NEW}) so
 *       audit entries are committed even when the caller rolls back.</li>
 * </ul>
 *
 * <h3>Read / query path</h3>
 * Use {@link AuditLogRepository} directly — it provides paginated, filtered,
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

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // -------------------------------------------------------------------------
    // Core write method
    // -------------------------------------------------------------------------

    /**
     * Builds and persists one {@link AuditLog} entry in a fresh transaction.
     *
     * <p>{@link Propagation#REQUIRES_NEW} suspends any existing transaction and
     * opens a new one, so the audit row is committed regardless of what the
     * caller's transaction does.
     *
     * @param username    AD sAMAccountName of the acting user — never null
     * @param action      action-type constant, e.g. {@code CONFIG_REFRESH} — never null
     * @param resourceId  optional resource identifier (e.g. table name, serial number)
     * @param description human-readable summary of what happened
     * @param ipAddress   originating client IP — null for batch / scheduled jobs
     * @param outcome     {@code SUCCESS} or {@code FAILURE}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String username,
                    String action,
                    @Nullable String resourceId,
                    @Nullable String description,
                    @Nullable String ipAddress,
                    String outcome) {

        AuditLog entry = AuditLog.builder()
                .username(username)
                .action(action)
                .resourceId(resourceId)
                .description(description)
                .ipAddress(ipAddress)
                .outcome(outcome)
                .build();

        auditLogRepository.save(entry);

        log.debug("Audit  user={}  action={}  resource={}  outcome={}  ip={}",
                  username, action, resourceId, outcome, ipAddress);
    }

    // -------------------------------------------------------------------------
    // Convenience overloads
    // -------------------------------------------------------------------------

    /**
     * Records a successful action — outcome is fixed to {@code SUCCESS}.
     *
     * @param username    AD username
     * @param action      action-type constant
     * @param resourceId  optional resource identifier
     * @param description human-readable summary
     * @param ipAddress   originating IP (null for batch jobs)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(String username,
                           String action,
                           @Nullable String resourceId,
                           @Nullable String description,
                           @Nullable String ipAddress) {

        log(username, action, resourceId, description, ipAddress, "SUCCESS");
    }

    /**
     * Records a failed action — outcome is fixed to {@code FAILURE}.
     *
     * @param username    AD username
     * @param action      action-type constant
     * @param resourceId  optional resource identifier
     * @param description failure reason / error message
     * @param ipAddress   originating IP (null for batch jobs)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String username,
                           String action,
                           @Nullable String resourceId,
                           @Nullable String description,
                           @Nullable String ipAddress) {

        log(username, action, resourceId, description, ipAddress, "FAILURE");
    }

    /**
     * Minimal overload for system / batch actions that have no HTTP context.
     *
     * <p>Sets {@code ipAddress = null} automatically — batch jobs run without
     * an inbound request.
     *
     * <p>Example:
     * <pre>{@code
     * auditLogService.logSystem("system", "CERT_EXPIRY_SCAN", "cert-expiry-job",
     *                           "Scanned 1024 certs, 3 expiring within 30 days");
     * }</pre>
     *
     * @param username    typically {@code "system"} or a scheduler job name
     * @param action      action-type constant
     * @param resourceId  optional resource identifier
     * @param description human-readable summary
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSystem(String username,
                          String action,
                          @Nullable String resourceId,
                          @Nullable String description) {

        log(username, action, resourceId, description, null, "SUCCESS");
    }
}
