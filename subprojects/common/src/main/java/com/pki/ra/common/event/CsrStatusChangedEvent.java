package com.pki.ra.common.event;

import com.pki.ra.common.model.enums.CsrStatus;

import java.time.Instant;

/**
 * Domain event published whenever a CSR request transitions to a new status.
 *
 * <p>Listeners are decoupled from the business logic:
 * <ul>
 *   <li>{@code CsrAuditEventListener} — writes to audit_log (async, after commit)</li>
 *   <li>Future: notification, SIEM, metrics listeners — add without changing service code</li>
 * </ul>
 *
 * <p>Published by {@code CsrTransitionService} — the single place where ALL
 * status changes happen. Business services never publish this directly.
 */
public record CsrStatusChangedEvent(
        String requestId,
        Long requestDbId,
        CsrStatus fromStatus,
        CsrStatus toStatus,
        String actorUsername,
        Long actorUserId,
        String actorRole,
        String remarks,
        String ipAddress,
        String csrProfile,
        Instant occurredAt
) {

    public String action() {
        return "CSR_" + toStatus.name();
    }

    public String description() {
        String transition = (fromStatus != null ? fromStatus.name() : "NULL") + " → " + toStatus.name();
        return remarks != null && !remarks.isBlank()
                ? transition + ": " + remarks
                : transition;
    }
}
