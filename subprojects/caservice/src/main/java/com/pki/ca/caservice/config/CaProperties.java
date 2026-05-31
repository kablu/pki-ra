package com.pki.ca.caservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ca")
public class CaProperties {

    /** Path to the PKCS#12 keystore holding Root CA and Intermediate CA keys+certs. */
    private String keystorePath = "ca-keystore.p12";

    /** Password for the keystore and all private-key entries within it. */
    private String keystorePassword = "changeit";

    /** Keystore alias for the Root CA key entry. */
    private String rootAlias = "root-ca";

    /** Keystore alias for the Intermediate CA key entry. */
    private String intermediateAlias = "intermediate-ca";

    /** Root CA certificate validity in years. */
    private int rootValidityYears = 20;

    /** Intermediate CA certificate validity in years. */
    private int intermediateValidityYears = 10;

    /** End-entity DSC validity in days. */
    private int endEntityValidityDays = 365;

    /**
     * Default algorithm used when bootstrapping the CA hierarchy.
     * Accepted values: RSA, ECDSA
     */
    private String defaultAlgorithm = "RSA";

    /** RSA key size (bits) — used when defaultAlgorithm=RSA. */
    private int rsaKeySize = 4096;

    /** EC named curve — used when defaultAlgorithm=ECDSA. */
    private String ecCurve = "P-384";

    /** Distinguished Name for the Root CA certificate. */
    private String rootDn = "CN=PKI Root CA, O=PKI Organization, OU=Certificate Authority, C=IN";

    /** Distinguished Name for the Intermediate CA certificate. */
    private String intermediateDn = "CN=PKI Intermediate CA, O=PKI Organization, OU=Certificate Authority, C=IN";

    /** CRL Distribution Point URL embedded in issued certificates (optional, leave empty to omit). */
    private String crlDistributionPoint = "";

    /** OCSP responder URL embedded in issued certificates (optional, leave empty to omit). */
    private String ocspUrl = "";
}
