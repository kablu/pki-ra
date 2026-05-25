package com.pki.ra.common.util.dto;

/**
 * Spring Data JPA interface projection for the
 * {@code countByAction} aggregate query in {@link com.pki.ra.common.util.AuditLogRepository}.
 *
 * <p>Spring Data automatically maps JPQL aliases ({@code action}, {@code count})
 * to the getter methods below — no manual casting or {@code Object[]} needed.
 *
 * <p>Usage in repository:
 * <pre>{@code
 * @Query("SELECT a.action AS action, COUNT(a) AS count
 *         FROM AuditLog a GROUP BY a.action ORDER BY COUNT(a) DESC")
 * List<ActionSummaryProjection> countByAction();
 * }</pre>
 *
 * <p>Usage in controller:
 * <pre>{@code
 * auditLogRepository.countByAction()
 *     .forEach(p -> summary.put(p.getAction(), p.getCount()));
 * }</pre>
 */
public interface ActionSummaryProjection {

    /** Action-type constant, e.g. {@code CONFIG_REFRESH}, {@code LOGIN}. */
    String getAction();

    /** Number of audit entries recorded for this action. */
    Long getCount();
}
