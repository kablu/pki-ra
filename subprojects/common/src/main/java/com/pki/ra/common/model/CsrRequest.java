package com.pki.ra.common.model;

import com.pki.ra.common.model.enums.ApprovalMode;
import com.pki.ra.common.model.enums.CsrProfile;
import com.pki.ra.common.model.enums.CsrStatus;
import com.pki.ra.common.model.enums.RequestPriority;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "csr_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CsrRequest extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Unique identifiers ---

    @Column(name = "request_id", nullable = false, unique = true, length = 50)
    private String requestId;

    @Column(name = "client_txn_id", nullable = false, unique = true, length = 100)
    private String clientTxnId;

    // --- CSR data ---

    @Column(name = "csr_pem", nullable = false, columnDefinition = "TEXT")
    private String csrPem;

    @Column(name = "subject_dn", nullable = false, length = 500)
    private String subjectDn;

    @Column(name = "key_algorithm", nullable = false, length = 20)
    private String keyAlgorithm;

    @Column(name = "key_size", nullable = false)
    private int keySize;

    @Column(name = "signature_algorithm", nullable = false, length = 50)
    private String signatureAlgorithm;

    @Column(name = "subject_alt_names", length = 2000)
    private String subjectAltNames;

    @Column(name = "csr_hash", nullable = false, length = 64)
    private String csrHash;

    // --- Profile ---

    @Enumerated(EnumType.STRING)
    @Column(name = "csr_profile", nullable = false, length = 50)
    private CsrProfile csrProfile;

    @Column(name = "template_id")
    private Long templateId;

    // --- Requestor info ---

    @Column(name = "requestor_name", nullable = false, length = 200)
    private String requestorName;

    @Column(name = "requestor_email", nullable = false, length = 200)
    private String requestorEmail;

    @Column(name = "requestor_department", length = 200)
    private String requestorDepartment;

    @Column(name = "requestor_phone", length = 50)
    private String requestorPhone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requestor_user_id")
    private User requestorUser;

    // --- Request details ---

    @Column(name = "requested_validity_days")
    private Integer requestedValidityDays;

    @Column(name = "purpose", length = 1000)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    @Builder.Default
    private RequestPriority priority = RequestPriority.NORMAL;

    @Column(name = "additional_attributes", columnDefinition = "JSON")
    private String additionalAttributes;

    // --- Transition status ---

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CsrStatus status = CsrStatus.RECEIVED;

    @Column(name = "status_reason", length = 1000)
    private String statusReason;

    // --- Validation result ---

    @Column(name = "validation_passed")
    private Boolean validationPassed;

    @Column(name = "validation_warnings", columnDefinition = "JSON")
    private String validationWarnings;

    @Column(name = "validation_flags", columnDefinition = "JSON")
    private String validationFlags;

    // --- Approval tracking ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "maker_remarks", length = 2000)
    private String makerRemarks;

    @Column(name = "maker_reviewed_at")
    private Instant makerReviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checker_id")
    private User checker;

    @Column(name = "checker_decision", length = 20)
    private String checkerDecision;

    @Column(name = "checker_remarks", length = 2000)
    private String checkerRemarks;

    @Column(name = "checker_decided_at")
    private Instant checkerDecidedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_id")
    private User closedBy;

    @Column(name = "closed_reason", length = 2000)
    private String closedReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode_at_pickup", length = 10)
    private ApprovalMode approvalModeAtPickup;

    // --- CA integration (async) ---

    @Column(name = "ca_transaction_id", length = 100)
    private String caTransactionId;

    @Column(name = "sent_to_ca_at")
    private Instant sentToCaAt;

    @Column(name = "post_back_url", length = 500)
    private String postBackUrl;

    @Column(name = "ca_response_received_at")
    private Instant caResponseReceivedAt;

    // --- Certificate link ---

    @Column(name = "certificate_id")
    private Long certificateId;
}
