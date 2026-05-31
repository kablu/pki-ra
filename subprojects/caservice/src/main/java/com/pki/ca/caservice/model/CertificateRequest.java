package com.pki.ca.caservice.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CertificateRequest {

    /**
     * PKCS#10 CSR encoded as PEM (with or without headers) or as base64 DER.
     * The service auto-detects the format.
     */
    @NotBlank(message = "CSR (pkcs10) must not be blank")
    private String pkcs10;

    /**
     * Requested validity in days. If null or ≤ 0, the CA default is used.
     */
    private Integer validityDays;

    /**
     * Optional additional Subject Alternative Names to embed (comma-separated emails or DNSs).
     * The CA adds them only when present; the CSR's own extensions still apply.
     */
    private String subjectAltNames;
}
