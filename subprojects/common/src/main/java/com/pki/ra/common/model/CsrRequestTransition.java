package com.pki.ra.common.model;

import com.pki.ra.common.model.enums.CsrStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Immutable record of every status change for a CSR request.
 * One row per transition — insert only, never updated or deleted.
 */
@Entity
@Table(name = "csr_request_transitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CsrRequestTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private CsrRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private CsrStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private CsrStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id", nullable = false)
    private User changedBy;

    @Column(name = "changed_by_role", nullable = false, length = 20)
    private String changedByRole;

    @Column(name = "remarks", length = 2000)
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
