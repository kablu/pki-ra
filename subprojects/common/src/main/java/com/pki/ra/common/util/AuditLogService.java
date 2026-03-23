package com.pki.ra.common.util;

import com.pki.ra.common.model.AuditLog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for persisting {@link AuditLog} entries.
 *
 * <p>Uses {@link Propagation#REQUIRES_NEW} so audit records are always
 * committed independently of the calling transaction — even if the
 * caller rolls back, the audit trail is preserved.
 *
 * <p><b>jSpecify:</b> {@code ipAddress} and {@code resourceId} are
 * {@link org.jspecify.annotations.Nullable @Nullable} — not all actions
 * originate from an HTTP request or target a specific resource.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Slf4j
@Service
public class AuditLogService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Records an audit log entry with REQUIRES_NEW propagation.
     *
     * @param username   AD sAMAccountName of the acting user (non-null)
     * @param action     action type constant, e.g. {@code CERT_REQUEST} (non-null)
     * @param resourceId optional identifier of the affected resource (nullable)
     * @param description human-readable summary (nullable)
     * @param ipAddress  originating IP address (nullable — batch jobs have no IP)
     * @param outcome    {@code SUCCESS} or {@code FAILURE}
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

        entityManager.persist(entry);
        log.debug("Audit: user={} action={} resource={} outcome={}", username, action, resourceId, outcome);
    }

    /**
     * Convenience method for logging a successful action.
     *
     * @param username    AD username
     * @param action      action type
     * @param resourceId  optional affected resource
     * @param description optional description
     * @param ipAddress   optional IP
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
     * Convenience method for logging a failed action.
     *
     * @param username    AD username
     * @param action      action type
     * @param resourceId  optional affected resource
     * @param description failure reason
     * @param ipAddress   optional IP
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String username,
                           String action,
                           @Nullable String resourceId,
                           @Nullable String description,
                           @Nullable String ipAddress) {
        log(username, action, resourceId, description, ipAddress, "FAILURE");
    }
}
