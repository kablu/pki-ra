package com.pki.ra.common.certificate.dto.csr;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class CsrRequestDto {

    private Long id;
    private String requestId;
    private String clientTxnId;

    private String subjectDn;
    private String keyAlgorithm;
    private int keySize;
    private String signatureAlgorithm;
    private String subjectAltNames;

    private String csrProfile;
    private String requestorName;
    private String requestorEmail;
    private String requestorDepartment;
    private Integer requestedValidityDays;
    private String purpose;
    private String priority;
    private Map<String, String> additionalAttributes;

    private String status;
    private String statusReason;
    private Boolean validationPassed;

    private String assignedToUsername;
    private Instant assignedAt;
    private String makerRemarks;
    private Instant makerReviewedAt;
    private String checkerUsername;
    private String checkerDecision;
    private String checkerRemarks;
    private Instant checkerDecidedAt;
    private String closedByUsername;
    private String closedReason;
    private Instant closedAt;

    private String approvalModeAtPickup;
    private Long certificateId;

    private Instant createdAt;
    private Instant updatedAt;

    private List<TransitionDto> transitions;

    @Data
    @Builder
    public static class TransitionDto {
        private String fromStatus;
        private String toStatus;
        private String changedByUsername;
        private String changedByRole;
        private String remarks;
        private Instant createdAt;
    }
}
