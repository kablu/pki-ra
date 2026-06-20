package com.pki.ra.common.certificate.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CertificateDto {

    private Long id;
    private String serialNumber;
    private String subjectDn;
    private String issuerDn;
    private Instant notBefore;
    private Instant notAfter;
    private String keyAlgorithm;
    private int keySize;
    private String signatureAlgorithm;
    private String status;
    private String fingerprintSha256;
    private Instant createdAt;
}
