package com.pki.ra.common.model.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines the VALID state transitions for CSR requests.
 * Any transition not explicitly listed here is BLOCKED.
 *
 * <p>This is the single source of truth for the state machine.
 * Services call {@link #canTransition(CsrStatus, CsrStatus)}
 * before changing status — never bypass this check.
 */
public final class CsrStatusTransition {

    private static final Map<CsrStatus, Set<CsrStatus>> ALLOWED =
            new EnumMap<>(CsrStatus.class);

    static {
        // Pre-approval transitions
        allow(CsrStatus.RECEIVED,          CsrStatus.VALIDATED, CsrStatus.VALIDATION_FAILED);
        allow(CsrStatus.VALIDATED,         CsrStatus.SUBMITTED);

        // Approval workflow transitions
        allow(CsrStatus.SUBMITTED,         CsrStatus.IN_REVIEW);
        allow(CsrStatus.IN_REVIEW,         CsrStatus.APPROVED, CsrStatus.REJECTED,
                                           CsrStatus.REVIEWED, CsrStatus.RETURNED);
        allow(CsrStatus.REVIEWED,          CsrStatus.APPROVED, CsrStatus.REJECTED);
        allow(CsrStatus.RETURNED,          CsrStatus.IN_REVIEW, CsrStatus.CLOSED);
        allow(CsrStatus.REJECTED,          CsrStatus.IN_REVIEW, CsrStatus.CLOSED);

        // Post-approval transitions
        allow(CsrStatus.APPROVED,          CsrStatus.ISSUED, CsrStatus.FAILED);
        allow(CsrStatus.FAILED,            CsrStatus.APPROVED);
    }

    private CsrStatusTransition() {}

    private static void allow(CsrStatus from, CsrStatus... targets) {
        ALLOWED.put(from, EnumSet.copyOf(Set.of(targets)));
    }

    /**
     * Check if a transition from → to is allowed.
     */
    public static boolean canTransition(CsrStatus from, CsrStatus to) {
        Set<CsrStatus> targets = ALLOWED.get(from);
        return targets != null && targets.contains(to);
    }

    /**
     * Get all valid target states from the given status.
     */
    public static Set<CsrStatus> validTargets(CsrStatus from) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(CsrStatus.class));
    }

    /**
     * Validate and throw if transition is not allowed.
     * Used by services before persisting a status change.
     */
    public static void requireValid(CsrStatus from, CsrStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    "Invalid status transition: " + from + " → " + to +
                    ". Allowed from " + from + ": " + validTargets(from));
        }
    }
}
