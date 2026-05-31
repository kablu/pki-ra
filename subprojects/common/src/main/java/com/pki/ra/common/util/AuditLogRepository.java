package com.pki.ra.common.util;

import com.pki.ra.common.model.AuditLog;
import com.pki.ra.common.util.dto.ActionSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * JPA repository for the {@code audit_log} table.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Read / query operations only.</li>
 *   <li>Write path is owned by {@link AuditLogService} (REQUIRES_NEW propagation).</li>
 * </ul>
 *
 * <h3>Specification Pattern (since design-pattern refactor)</h3>
 * This repository now extends {@link JpaSpecificationExecutor}, which exposes
 * {@code findAll(Specification, Pageable)} and friends. All former derived-query
 * methods (findByUsername…, findByAction…, etc.) have been removed — callers
 * compose {@link com.pki.ra.common.util.AuditLogSpecification} predicates instead:
 *
 * <pre>{@code
 * Specification<AuditLog> spec = Specification
 *     .where(AuditLogSpecification.hasUsername(username))
 *     .and(AuditLogSpecification.hasAction(action))
 *     .and(AuditLogSpecification.hasOutcome(outcome));
 *
 * Page<AuditLog> page = auditLogRepository.findAll(spec, pageable);
 * }</pre>
 *
 * <h3>Query categories retained</h3>
 * <ol>
 *   <li>Count / exists — for quick statistics and anomaly detection.</li>
 *   <li>Aggregate — {@link ActionSummaryProjection} for dashboard summaries.</li>
 * </ol>
 *
 * <p>Registered automatically by
 * {@code @EnableJpaRepositories(basePackages = "com.pki.ra")} in {@code DatabaseConfig}.
 */
public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>,
                JpaSpecificationExecutor<AuditLog> {

    // =========================================================================
    // Count / exists — for quick statistics and anomaly detection
    // =========================================================================

    /** Total number of audit entries recorded for a specific user. */
    long countByUsername(String username);

    /** Total number of audit entries for a specific action type. */
    long countByAction(String action);

    /** Total number of entries with a given outcome ({@code SUCCESS} / {@code FAILURE}). */
    long countByOutcome(String outcome);

    /** Total failures recorded for a specific user — useful for anomaly detection. */
    long countByUsernameAndOutcome(String username, String outcome);

    /** Returns {@code true} if at least one audit entry exists for this username. */
    boolean existsByUsername(String username);

    // =========================================================================
    // Aggregate queries — for dashboards and summaries
    // =========================================================================

    /**
     * Returns action-level entry counts — ordered by count descending.
     *
     * <p>Uses {@link ActionSummaryProjection} so Spring Data maps JPQL aliases
     * ({@code action}, {@code count}) directly to typed getters —
     * no {@code Object[]} casting needed.
     *
     * <p>Example result:
     * <pre>
     *   LOGIN          → 203
     *   CERT_REQUEST   → 45
     *   CONFIG_REFRESH → 12
     * </pre>
     */
    @Query("""
            SELECT a.action AS action, COUNT(a) AS count
            FROM AuditLog a
            GROUP BY a.action
            ORDER BY COUNT(a) DESC
            """)
    List<ActionSummaryProjection> summariseByAction();

    /**
     * Returns outcome counts ({@code SUCCESS} / {@code FAILURE}) across the entire table.
     * Useful for a quick health-check: how many failures vs successes?
     */
    @Query("""
            SELECT a.outcome AS action, COUNT(a) AS count
            FROM AuditLog a
            GROUP BY a.outcome
            ORDER BY COUNT(a) DESC
            """)
    List<ActionSummaryProjection> summariseByOutcome();
}
