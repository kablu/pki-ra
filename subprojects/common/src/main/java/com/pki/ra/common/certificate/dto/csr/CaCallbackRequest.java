package com.pki.ra.common.certificate.dto.csr;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO received FROM external CA (WLCA) when it posts the signed
 * certificate back to RA's callback endpoint (postBackUrl).
 */
@Data
public class CaCallbackRequest {

    @NotBlank(message = "Request ID is required")
    private String requestId;

    private String caTransactionId;

    @NotBlank(message = "Status is required (ISSUED or FAILED)")
    private String status;

    private String certificatePem;
    private String certificateChain;
    private String serialNumber;
    private String signatureAlgorithm;
    private String subject;
    private String issuer;
    private String notBefore;
    private String notAfter;

    private String failureReason;
}
