package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Certificate extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_number", nullable = false, unique = true, length = 64)
    private String serialNumber;

    @Column(name = "subject_dn", nullable = false, length = 500)
    private String subjectDn;

    @Column(name = "issuer_dn", nullable = false, length = 500)
    private String issuerDn;

    @Column(name = "not_before", nullable = false)
    private Instant notBefore;

    @Column(name = "not_after", nullable = false)
    private Instant notAfter;

    @Column(name = "certificate_pem", nullable = false, columnDefinition = "TEXT")
    private String certificatePem;

    @Column(name = "certificate_chain", nullable = false, columnDefinition = "TEXT")
    private String certificateChain;

    @Column(name = "key_algorithm", nullable = false, length = 20)
    private String keyAlgorithm;

    @Column(name = "key_size", nullable = false)
    private int keySize;

    @Column(name = "signature_algorithm", nullable = false, length = 50)
    private String signatureAlgorithm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CertificateStatus status = CertificateStatus.ACTIVE;

    @Column(name = "fingerprint_sha256", nullable = false, unique = true, length = 64)
    private String fingerprintSha256;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private CertificateRequest request;

    public enum CertificateStatus {
        ACTIVE, REVOKED, EXPIRED, SUSPENDED
    }
}
