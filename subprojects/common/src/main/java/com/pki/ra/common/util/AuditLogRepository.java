package com.pki.ra.common.util;

import com.pki.ra.common.model.AuditLog;
import com.pki.ra.common.util.dto.ActionSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * JPA repository for querying the {@code audit_log} table.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Read / query operations only.</li>
 *   <li>Write path is owned by {@link AuditLogService} (REQUIRES_NEW propagation).</li>
 * </ul>
 *
 * <h3>Query categories</h3>
 * <ol>
 *   <li>Paginated — for REST endpoints that return large datasets.</li>
 *   <li>List — for filtered lookups (by user, action, outcome, resource).</li>
 *   <li>Count / exists — for quick statistics and existence checks.</li>
 *   <li>Aggregate — {@link ActionSummaryProjection} for dashboard summary.</li>
 * </ol>
 *
 * <p>Registered automatically by
 * {@code @EnableJpaRepositories(basePackages = "com.pki.ra")} in {@code DatabaseConfig}.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // =========================================================================
    // Paginated queries — use these for REST endpoints
    // =========================================================================

    /**
     * All audit entries — newest first, paginated.
     * Default page size is enforced in the controller (max 100).
     */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * All entries for a given username — newest first, paginated.
     * Use when a single user may have thousands of entries.
     */
    Page<AuditLog> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    /**
     * All entries for a given action type — newest first, paginated.
     * Use when a high-frequency action (e.g. LOGIN) may have thousands of entries.
     */
    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    /**
     * All entries for a given outcome — newest first, paginated.
     * Useful for "show me all failures" queries on large datasets.
     */
    Page<AuditLog> findByOutcomeOrderByCreatedAtDesc(String outcome, Pageable pageable);

    // =========================================================================
    // List queries — for targeted lookups expected to return small sets
    // =========================================================================

    /**
     * All entries for a given username and action — newest first.
     * Example: "Every CONFIG_REFRESH done by admin."
     */
    List<AuditLog> findByUsernameAndActionOrderByCreatedAtDesc(String username, String action);

    /**
     * All entries for a given username and outcome — newest first.
     * Example: "Every failed action by john.doe."
     */
    List<AuditLog> findByUsernameAndOutcomeOrderByCreatedAtDesc(String username, String outcome);

    /**
     * All entries within a UTC time window — newest first.
     * Example: "Everything that happened between 09:00 and 10:00 today."
     */
    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);

    /**
     * All entries for a specific resource identifier — newest first.
     * Example: "Who touched the LDAP config rows?"
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.resourceId = :resourceId
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> findByResourceId(@Param("resourceId") String resourceId);

    // =========================================================================
    // Count / exists queries — for quick statistics
    // =========================================================================

    /** Total number of audit entries recorded for a specific user. */
    long countByUsername(String username);

    /** Total number of audit entries for a specific action type. */
    long countByAction(String action);

    /** Total number of entries with a given outcome (SUCCESS / FAILURE). */
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
     * <p>Uses a {@link ActionSummaryProjection} interface projection so Spring Data
     * maps the JPQL aliases ({@code action}, {@code count}) directly to typed
     * getters — no {@code Object[]} casting needed.
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
     * Returns outcome counts (SUCCESS / FAILURE) across the entire table.
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
