package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "approval_matrix")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalMatrix extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "csr_profile", length = 50, unique = true)
    private String csrProfile;

    @Column(name = "approval_mode", nullable = false, length = 10)
    @Builder.Default
    private String approvalMode = "DUAL";

    @Column(name = "maker_role", nullable = false, length = 50)
    @Builder.Default
    private String makerRole = "ROLE_OPERATOR";

    @Column(name = "checker_role", nullable = false, length = 50)
    @Builder.Default
    private String checkerRole = "ROLE_OPERATOR";

    @Column(name = "admin_can_be_maker", nullable = false)
    @Builder.Default
    private boolean adminCanBeMaker = false;

    @Column(name = "admin_can_be_checker", nullable = false)
    @Builder.Default
    private boolean adminCanBeChecker = false;

    @Column(name = "checker_can_return", nullable = false)
    @Builder.Default
    private boolean checkerCanReturn = false;

    @Column(name = "min_remarks_length", nullable = false)
    @Builder.Default
    private int minRemarksLength = 0;

    @Column(name = "auto_approve", nullable = false)
    @Builder.Default
    private boolean autoApprove = false;

    @Column(name = "urgent_auto_escalate", nullable = false)
    @Builder.Default
    private boolean urgentAutoEscalate = false;

    @Column(name = "max_pending_per_operator", nullable = false)
    @Builder.Default
    private int maxPendingPerOperator = 10;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
