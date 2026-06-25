package com.pki.ra.common.certificate.dto.csr;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CsrSubmitRequest {

    @NotBlank(message = "PKCS#10 CSR is required")
    private String pkcs10;

    @NotBlank(message = "Client transaction ID is required")
    @Size(max = 100)
    private String clientTxnId;

    @NotBlank(message = "CSR profile is required")
    private String csrProfile;

    @NotBlank(message = "Requestor name is required")
    @Size(max = 200)
    private String requestorName;

    @NotBlank(message = "Requestor email is required")
    @Email(message = "Invalid email format")
    @Size(max = 200)
    private String requestorEmail;

    @Size(max = 200)
    private String requestorDepartment;

    @Size(max = 50)
    private String requestorPhone;

    private Integer validityDays;

    @Size(max = 2000)
    private String subjectAltNames;

    @Size(max = 1000)
    private String purpose;

    private String priority;

    private Map<String, String> additionalAttributes;
}
