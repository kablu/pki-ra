package com.pki.ra.common.certificate;

/**
 * Abstraction over the Certificate Authority backend.
 * Phase 1 uses a REST-based client calling caservice.
 * Phase 8 will add multi-CA connectors (EJBCA, Vault, etc.).
 */
public interface CaClient {

    /**
     * Signs a PKCS#10 CSR and returns the issued certificate with chain.
     *
     * @param csrPem       PKCS#10 CSR in PEM format
     * @param validityDays requested validity (null = CA default)
     * @param sans         comma-separated SANs (null = from CSR only)
     * @return signing result containing PEM certificate, chain, serial, and metadata
     */
    CaSigningResult sign(String csrPem, Integer validityDays, String sans);

    record CaSigningResult(
            String certificatePem,
            String certificateChain,
            String serialNumber,
            String signatureAlgorithm,
            String subject,
            String issuer,
            String notBefore,
            String notAfter
    ) {}
}
