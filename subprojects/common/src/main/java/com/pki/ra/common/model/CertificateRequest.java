package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "certificate_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificateRequest extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "csr_pem", nullable = false, columnDefinition = "TEXT")
    private String csrPem;

    @Column(name = "subject_dn", nullable = false, length = 500)
    private String subjectDn;

    @Column(name = "key_algorithm", nullable = false, length = 20)
    private String keyAlgorithm;

    @Column(name = "key_size", nullable = false)
    private int keySize;

    @Column(name = "subject_alt_names", length = 2000)
    private String subjectAltNames;

    @Column(name = "requested_validity_days")
    private Integer requestedValidityDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "status_reason", length = 1000)
    private String statusReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id")
    private Certificate certificate;

    public enum RequestStatus {
        PENDING, APPROVED, REJECTED, ISSUED, FAILED
    }
}
