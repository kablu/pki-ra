package com.pki.ra.raservice.csr;

import com.pki.ra.common.event.CsrStatusChangedEvent;
import com.pki.ra.common.util.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Centralized audit listener for ALL CSR status transitions.
 *
 * <p>Design:
 * <ul>
 *   <li>{@code @TransactionalEventListener(AFTER_COMMIT)} — fires only after
 *       the business transaction commits successfully. If the transaction
 *       rolls back, no audit entry is created (correct — nothing happened).</li>
 *   <li>{@code @Async("auditExecutor")} — runs in a background thread so
 *       the API response is not blocked by audit I/O.</li>
 *   <li>Single listener for ALL transitions — no scattered audit calls
 *       in service methods. Adding new audit consumers = new listener class,
 *       zero change to business code (Open/Closed principle).</li>
 * </ul>
 */
@Slf4j
@Component
public class CsrAuditEventListener {

    private final AuditLogService auditLogService;

    public CsrAuditEventListener(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Async("auditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(CsrStatusChangedEvent event) {
        try {
            auditLogService.logSuccess(
                    event.actorUsername(),
                    event.action(),
                    event.requestId(),
                    event.description(),
                    event.ipAddress(),
                    event.actorUserId()
            );

            log.debug("Audit logged: {} {} by {} ({})",
                    event.requestId(), event.action(),
                    event.actorUsername(), event.actorRole());

        } catch (Exception e) {
            log.error("Failed to write audit for {} {}: {}",
                    event.requestId(), event.action(), e.getMessage(), e);
        }
    }
}
