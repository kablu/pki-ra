package com.pki.ra.common.certificate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CsrSubmitRequest {

    @NotBlank(message = "PKCS#10 CSR is required")
    private String pkcs10;

    private Integer validityDays;

    private String subjectAltNames;
}
