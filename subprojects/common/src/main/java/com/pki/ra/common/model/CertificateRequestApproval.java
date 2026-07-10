package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "certificate_request_approvals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificateRequestApproval extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private CertificateRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private ApprovalDecision decision;

    @Column(name = "comment", length = 1000)
    private String comment;

    public enum ApprovalDecision {
        APPROVED, REJECTED
    }
}
