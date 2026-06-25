package com.pki.ra.common.model.enums;

/**
 * Configurable assignment mode — how CSR requests are assigned
 * to operators for review.
 */
public enum AssignmentMode {

    SELF_PICKUP("Operators pick requests from pool independently"),
    ADMIN_ASSIGN("Admin assigns each request manually"),
    HYBRID("Operators can self-pickup; Admin can also assign");

    private final String description;

    AssignmentMode(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
