package com.pki.ra.common.model.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * All possible statuses for a CSR request, covering both
 * pre-approval (intake) and approval workflow phases.
 *
 * <p>Transition rules are enforced by {@link CsrStatusTransition}.
 */
public enum CsrStatus {

    // --- Pre-approval (intake) ---
    RECEIVED("CSR received, validation pending"),
    VALIDATED("7-layer validation passed"),
    VALIDATION_FAILED("Validation failed — client must resubmit"),
    SUBMITTED("Queued for approval workflow"),

    // --- Approval workflow ---
    IN_REVIEW("Operator reviewing (Maker in DUAL mode)"),
    REVIEWED("Maker submitted remarks — awaiting Checker (DUAL only)"),
    APPROVED("Approved — sending to CA"),
    REJECTED("Rejected — Admin can reassign or close"),
    RETURNED("Returned to Admin by operator"),
    CLOSED("Permanently closed by Admin — true final state"),

    // --- Post-approval ---
    SENT_TO_CA("CSR sent to external CA (WLCA) — awaiting callback"),
    ISSUED("CA signed — certificate received via callback"),
    FAILED("CA signing failed — Admin can retry");

    private final String description;

    CsrStatus(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /**
     * True final states — no further transitions possible.
     */
    public static final Set<CsrStatus> TERMINAL_STATES =
            EnumSet.of(CLOSED, ISSUED, VALIDATION_FAILED);

    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }

    /**
     * States that are visible in the approval pool (operators can pick up).
     */
    public static final Set<CsrStatus> POOL_STATES =
            EnumSet.of(SUBMITTED);

    /**
     * States where Admin action is needed.
     */
    public static final Set<CsrStatus> ADMIN_ACTION_STATES =
            EnumSet.of(REJECTED, RETURNED, FAILED);
}
