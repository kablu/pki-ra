package com.pki.ra.common.certificate.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class CertificateRequestDto {

    private Long id;
    private String subjectDn;
    private String keyAlgorithm;
    private int keySize;
    private String subjectAltNames;
    private Integer requestedValidityDays;
    private String status;
    private String statusReason;
    private String requesterUsername;
    private Long certificateId;
    private String certificateSerial;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ApprovalDto> approvals;

    @Data
    @Builder
    public static class ApprovalDto {
        private String approverUsername;
        private String decision;
        private String comment;
        private Instant createdAt;
    }
}
