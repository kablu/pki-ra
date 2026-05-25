package com.pki.ra.common.util;

import com.pki.ra.common.model.AuditLog;
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
 * <p>Write path stays in {@link AuditLogService} (REQUIRES_NEW propagation).
 * This repository is used only for read / admin queries.
 *
 * <p>Registered automatically by {@code @EnableJpaRepositories(basePackages = "com.pki.ra")}
 * in {@code DatabaseConfig}.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** All entries for a given user — newest first. */
    List<AuditLog> findByUsernameOrderByCreatedAtDesc(String username);

    /** All entries for a given action type — newest first. */
    List<AuditLog> findByActionOrderByCreatedAtDesc(String action);

    /** All entries for a given outcome (SUCCESS / FAILURE) — newest first. */
    List<AuditLog> findByOutcomeOrderByCreatedAtDesc(String outcome);

    /** Paginated full list — newest first. */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Entries between two instants — newest first. */
    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);

    /** Entries matching username AND action — newest first. */
    List<AuditLog> findByUsernameAndActionOrderByCreatedAtDesc(String username, String action);

    /** Count of entries per action type — used for dashboards. */
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a GROUP BY a.action ORDER BY COUNT(a) DESC")
    List<Object[]> countByAction();

    /** Recent N entries for a specific resource. */
    @Query("SELECT a FROM AuditLog a WHERE a.resourceId = :resourceId ORDER BY a.createdAt DESC")
    List<AuditLog> findByResourceId(@Param("resourceId") String resourceId);
}
