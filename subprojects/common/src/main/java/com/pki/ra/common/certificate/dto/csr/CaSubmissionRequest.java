package com.pki.ra.common.certificate.dto.csr;

import lombok.Builder;
import lombok.Data;

/**
 * DTO sent FROM RA TO external CA (WLCA) when submitting an approved CSR.
 */
@Data
@Builder
public class CaSubmissionRequest {

    private String requestId;
    private String csrPem;
    private String csrProfile;
    private Integer validityDays;
    private String subjectAltNames;
    private String postBackUrl;
}
