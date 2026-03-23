package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base JPA entity providing automatic audit fields.
 *
 * <p>All pki-ra entities should extend this class to automatically populate:
 * <ul>
 *   <li>{@code createdAt}  — timestamp when record was inserted</li>
 *   <li>{@code createdBy}  — AD username who created the record</li>
 *   <li>{@code updatedAt}  — timestamp of last update</li>
 *   <li>{@code updatedBy}  — AD username who last modified the record</li>
 * </ul>
 *
 * <p>Requires {@code @EnableJpaAuditing} on a {@code @Configuration} class
 * and a {@code AuditorAware<String>} bean returning the current AD principal.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditEntity {

    /** Timestamp when this record was first persisted. Set automatically by JPA Auditing. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** AD username (sAMAccountName) of the user who created this record. */
    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    /** Timestamp of the most recent update to this record. */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** AD username of the user who last modified this record. */
    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;
}
