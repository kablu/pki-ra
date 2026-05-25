package com.pki.ra.common.util;

import com.pki.ra.common.model.AuditLog;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * Factory of JPA {@link Specification} predicates for {@link AuditLog} queries.
 *
 * <h3>Design Pattern — Specification</h3>
 * Each static method returns a reusable, composable predicate. Callers chain them
 * with {@code Specification.where(...).and(...).and(...)} to build any combination
 * without adding a new derived-query method to the repository for every combination.
 *
 * <h3>Null-safe</h3>
 * Every method returns {@code null} when the filter value is {@code null} or blank.
 * Spring Data JPA treats a {@code null} Specification as "no filter" — so
 * optional parameters compose cleanly without {@code if/else} in the caller.
 *
 * <h3>Before this class existed (8 derived-query methods in repository)</h3>
 * <pre>
 *   findByUsernameOrderByCreatedAtDesc(username, pageable)
 *   findByActionOrderByCreatedAtDesc(action, pageable)
 *   findByOutcomeOrderByCreatedAtDesc(outcome, pageable)
 *   findByUsernameAndActionOrderByCreatedAtDesc(username, action)
 *   findByUsernameAndOutcomeOrderByCreatedAtDesc(username, outcome)
 *   findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)
 *   findByResourceId(resourceId)
 *   findAllByOrderByCreatedAtDesc(pageable)
 * </pre>
 *
 * <h3>After — one repository method + composable specs</h3>
 * <pre>
 *   Specification spec = Specification
 *       .where(hasUsername(username))
 *       .and(hasAction(action))
 *       .and(hasOutcome(outcome));
 *
 *   auditLogRepository.findAll(spec, pageable);   // one call covers every case
 * </pre>
 *
 * @see AuditLogRepository
 */
public final class AuditLogSpecification {

    private AuditLogSpecification() { /* utility class — no instances */ }

    // =========================================================================
    // Equality predicates
    // =========================================================================

    /**
     * Matches rows where {@code username = :username}.
     * Returns {@code null} (no-op) when {@code username} is null or blank.
     */
    public static Specification<AuditLog> hasUsername(String username) {
        return isBlank(username) ? null :
                (root, query, cb) -> cb.equal(root.get("username"), username);
    }

    /**
     * Matches rows where {@code action = :action}.
     * Returns {@code null} (no-op) when {@code action} is null or blank.
     */
    public static Specification<AuditLog> hasAction(String action) {
        return isBlank(action) ? null :
                (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    /**
     * Matches rows where {@code outcome = :outcome} (case-insensitive normalised to upper).
     * Returns {@code null} (no-op) when {@code outcome} is null or blank.
     */
    public static Specification<AuditLog> hasOutcome(String outcome) {
        return isBlank(outcome) ? null :
                (root, query, cb) -> cb.equal(root.get("outcome"), outcome.toUpperCase());
    }

    /**
     * Matches rows where {@code resource_id = :resourceId}.
     * Returns {@code null} (no-op) when {@code resourceId} is null or blank.
     */
    public static Specification<AuditLog> hasResourceId(String resourceId) {
        return isBlank(resourceId) ? null :
                (root, query, cb) -> cb.equal(root.get("resourceId"), resourceId);
    }

    // =========================================================================
    // Range predicates
    // =========================================================================

    /**
     * Matches rows where {@code created_at >= :from}.
     * Returns {@code null} (no-op) when {@code from} is null.
     */
    public static Specification<AuditLog> createdAfter(Instant from) {
        return from == null ? null :
                (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    /**
     * Matches rows where {@code created_at <= :to}.
     * Returns {@code null} (no-op) when {@code to} is null.
     */
    public static Specification<AuditLog> createdBefore(Instant to) {
        return to == null ? null :
                (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
