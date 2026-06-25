package com.pki.ra.common.model.enums;

/**
 * Configurable approval mode — how many operators are
 * required to process a CSR request.
 *
 * <p>Stored in app_config (config_type = CSR_WORKFLOW, key = approval_mode).
 * Admin can change at runtime via PUT /api/ra/admin/config/workflow.
 */
public enum ApprovalMode {

    SINGLE("One operator reviews and approves/rejects directly"),
    DUAL("Maker reviews + Checker accepts/rejects (separation of duties)");

    private final String description;

    ApprovalMode(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
