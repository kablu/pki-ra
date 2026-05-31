package com.pki.ca.caservice.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CertificateResponse {

    /** Issued end-entity certificate in PEM format. */
    private String certificate;

    /** Intermediate CA certificate in PEM format (for chain building). */
    private String intermediateCertificate;

    /** Root CA certificate in PEM format (trust anchor). */
    private String rootCertificate;

    /** Full certificate chain (end-entity → intermediate → root) in PEM format. */
    private String certificateChain;

    /** Serial number of the issued certificate (hex string). */
    private String serialNumber;

    /** Algorithm used by the issued certificate (e.g. "SHA256withRSA"). */
    private String signatureAlgorithm;

    /** ISO-8601 string: when the certificate becomes valid. */
    private String notBefore;

    /** ISO-8601 string: when the certificate expires. */
    private String notAfter;

    /** Subject DN extracted from the issued certificate. */
    private String subject;
}
