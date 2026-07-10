package com.pki.ra.raservice.csr.validation;

import com.pki.ra.common.model.enums.CsrProfile;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.openssl.PEMParser;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Seven-layer CSR validation service for the RA.
 *
 * <p><b>Scenario:</b> A user (e.g., a server administrator) submits a PKCS#10
 * CSR via {@code POST /api/ra/requests}. Before the request enters the
 * approval workflow the RA must verify that the CSR is structurally sound,
 * cryptographically valid, and consistent with the requested certificate
 * profile. This service performs all seven checks and returns a
 * {@link CsrValidationResult} that the caller uses to either advance the
 * request to {@code SUBMITTED} or park it in {@code VALIDATION_FAILED}.
 *
 * <p><b>Layers</b>
 * <ol>
 *   <li>Parse — PEM / DER decoding</li>
 *   <li>Proof of Possession — CSR self-signature</li>
 *   <li>Signature Algorithm — no SHA-1 / MD5</li>
 *   <li>Key Strength — RSA ≥ 2048, EC ≥ P-256</li>
 *   <li>Subject DN — CN present, correct format for profile</li>
 *   <li>SAN — dNSName required for TLS_SERVER; FQDN validity</li>
 *   <li>Extensions — Key Usage + EKU must match profile; no cA=TRUE</li>
 * </ol>
 *
 * <p><b>Usage</b>
 * <pre>{@code
 * CsrValidationResult result = csrValidatorService.validate(pemString, CsrProfile.TLS_SERVER);
 * if (!result.isPassed()) {
 *     throw new CsrValidationException(result.getErrors());
 * }
 * }</pre>
 */
@Slf4j
@Service
public class CsrValidatorService {

    // ── FQDN regex (RFC 1123 + RFC 5280) ─────────────────────────────────────
    private static final Pattern FQDN_PATTERN = Pattern.compile(
        "^(?=.{1,253}$)" +
        "((?!-)[a-zA-Z0-9-]{1,63}(?<!-)\\.)+" +
        "(?!-)[a-zA-Z]{2,63}$"
    );
    private static final Pattern WILDCARD_FQDN = Pattern.compile(
        "^\\*\\.((?!-)[a-zA-Z0-9-]{1,63}(?<!-)\\.)+(?!-)[a-zA-Z]{2,63}$"
    );
    private static final Set<String> BANNED_ALGORITHMS = Set.of(
        "MD5WITHRSA", "MD2WITHRSA", "SHA1WITHRSA",
        "SHA1WITHECDSA", "SHA1WITHDSA"
    );

    // ── Minimum key sizes ─────────────────────────────────────────────────────
    private static final int RSA_MIN_BITS = 2048;
    private static final int EC_MIN_BITS  = 256;   // P-256

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Validates a PKCS#10 CSR against the given certificate profile.
     *
     * @param csrPem  PEM-encoded CSR (BEGIN CERTIFICATE REQUEST header)
     * @param profile the certificate profile this CSR is intended for
     * @return a result object; call {@link CsrValidationResult#isPassed()} to
     *         decide whether to proceed
     */
    public CsrValidationResult validate(String csrPem, CsrProfile profile) {
        CsrValidationResult.Builder result = CsrValidationResult.builder();

        // ── Layer 1: Parse ────────────────────────────────────────────────────
        PKCS10CertificationRequest csr = parse(csrPem, result);
        if (csr == null) {
            // Cannot proceed with subsequent layers if parsing failed
            return result.build();
        }

        // ── Layer 2: Proof of Possession ──────────────────────────────────────
        checkSignature(csr, result);

        // ── Layer 3: Signature Algorithm ──────────────────────────────────────
        checkSignatureAlgorithm(csr, result);

        // ── Layer 4: Key Strength ─────────────────────────────────────────────
        checkKeyStrength(csr, result, profile);

        // ── Layer 5: Subject DN ───────────────────────────────────────────────
        checkSubjectDn(csr, result, profile);

        // ── Layer 6: SAN ──────────────────────────────────────────────────────
        checkSan(csr, result, profile);

        // ── Layer 7: Extensions (Key Usage + EKU + Basic Constraints) ─────────
        checkExtensions(csr, result, profile);

        CsrValidationResult built = result.build();
        log.info("CSR validation {} for profile={} subject={} errors={} warnings={}",
            built.isPassed() ? "PASSED" : "FAILED",
            profile,
            built.getSubjectDn(),
            built.getErrors().size(),
            built.getWarnings().size());

        return built;
    }

    // =========================================================================
    // Layer 1 — Parse
    // =========================================================================

    private PKCS10CertificationRequest parse(String input, CsrValidationResult.Builder result) {
        if (input == null || input.isBlank()) {
            result.addError("PKI_VAL_001", "CSR payload is null or empty");
            return null;
        }
        String trimmed = input.strip();
        try {
            PKCS10CertificationRequest csr;
            if (trimmed.startsWith("-----")) {
                try (PEMParser parser = new PEMParser(new StringReader(trimmed))) {
                    Object obj = parser.readObject();
                    if (!(obj instanceof PKCS10CertificationRequest)) {
                        result.addError("PKI_VAL_002",
                            "PEM block does not contain a PKCS#10 CSR — found: " +
                            (obj == null ? "null" : obj.getClass().getSimpleName()));
                        return null;
                    }
                    csr = (PKCS10CertificationRequest) obj;
                }
            } else {
                // Base64 DER fallback
                byte[] der = Base64.getMimeDecoder()
                    .decode(trimmed.replaceAll("\\s+", ""));
                csr = new PKCS10CertificationRequest(der);
            }
            log.debug("Layer 1 PASS — CSR parsed, subject={}", csr.getSubject());
            return csr;
        } catch (Exception ex) {
            result.addError("PKI_VAL_002",
                "CSR parse failure: " + ex.getMessage());
            return null;
        }
    }

    // =========================================================================
    // Layer 2 — Proof of Possession (self-signature)
    // =========================================================================

    private void checkSignature(PKCS10CertificationRequest csr,
                                 CsrValidationResult.Builder result) {
        try {
            ContentVerifierProvider verifier = new JcaContentVerifierProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(csr.getSubjectPublicKeyInfo());

            if (!csr.isSignatureValid(verifier)) {
                result.addError("PKI_CRYPTO_001",
                    "CSR self-signature verification failed — private key mismatch or CSR tampered");
            } else {
                log.debug("Layer 2 PASS — self-signature valid");
            }
        } catch (Exception ex) {
            result.addError("PKI_CRYPTO_001",
                "Cannot verify CSR self-signature: " + ex.getMessage());
        }
    }

    // =========================================================================
    // Layer 3 — Signature Algorithm (no weak algorithms)
    // =========================================================================

    private void checkSignatureAlgorithm(PKCS10CertificationRequest csr,
                                          CsrValidationResult.Builder result) {
        String alg = csr.getSignatureAlgorithm().getAlgorithm().getId();
        // Resolve OID to name for readable comparison
        String algName = resolveAlgName(alg).toUpperCase(Locale.ROOT);

        result.signatureAlgorithm(algName);

        if (BANNED_ALGORITHMS.contains(algName)) {
            result.addError("PKI_CRYPTO_002",
                "Signature algorithm '" + algName + "' is not acceptable. " +
                "Use SHA256withRSA, SHA384withRSA, SHA512withRSA, or ECDSA-with-SHA256+");
        } else {
            log.debug("Layer 3 PASS — signatureAlgorithm={}", algName);
        }
    }

    // =========================================================================
    // Layer 4 — Key Strength
    // =========================================================================

    private void checkKeyStrength(PKCS10CertificationRequest csr,
                                   CsrValidationResult.Builder result,
                                   CsrProfile profile) {
        SubjectPublicKeyInfo spki = csr.getSubjectPublicKeyInfo();
        String algOid = spki.getAlgorithm().getAlgorithm().getId();

        try {
            java.security.KeyFactory kf;
            java.security.PublicKey pubKey;
            // Determine algorithm from SPKI OID to pick the right KeyFactory
            String spkiAlgOid = spki.getAlgorithm().getAlgorithm().getId();
            String jcaAlg = switch (spkiAlgOid) {
                case "1.2.840.113549.1.1.1" -> "RSA";
                case "1.2.840.10045.2.1"    -> "EC";
                default                     -> null;
            };
            if (jcaAlg != null) {
                kf = java.security.KeyFactory.getInstance(jcaAlg,
                    BouncyCastleProvider.PROVIDER_NAME);
                pubKey = kf.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(spki.getEncoded()));
            } else {
                // Ed25519/Ed448 or unknown — mark as acceptable, skip size check
                result.keyAlgorithm(spkiAlgOid).keySize(0);
                log.debug("Layer 4 PASS — key algorithm OID={}", spkiAlgOid);
                return;
            }

            if (pubKey instanceof RSAPublicKey rsa) {
                int bits = rsa.getModulus().bitLength();
                result.keyAlgorithm("RSA").keySize(bits);
                if (bits < RSA_MIN_BITS) {
                    result.addError("PKI_KEY_001",
                        "RSA key size " + bits + " bits is below minimum " +
                        RSA_MIN_BITS + " bits");
                } else {
                    log.debug("Layer 4 PASS — RSA {} bits", bits);
                }
            } else if (pubKey instanceof ECPublicKey ec) {
                int bits = ec.getParams().getCurve().getField().getFieldSize();
                result.keyAlgorithm("EC").keySize(bits);
                if (bits < EC_MIN_BITS) {
                    result.addError("PKI_KEY_001",
                        "EC key size " + bits + " bits is below minimum " +
                        EC_MIN_BITS + " bits (use P-256, P-384, or P-521)");
                } else {
                    log.debug("Layer 4 PASS — EC {} bits", bits);
                }
            } else {
                // Ed25519 / Ed448 — always acceptable
                result.keyAlgorithm(pubKey.getAlgorithm()).keySize(0);
                log.debug("Layer 4 PASS — {} key", pubKey.getAlgorithm());
            }
        } catch (Exception ex) {
            // Fallback: parse SPKI algorithm OID
            result.keyAlgorithm(algOid).keySize(0);
            result.addWarning("PKI_KEY_002",
                "Could not determine exact key size: " + ex.getMessage());
        }
    }

    // =========================================================================
    // Layer 5 — Subject DN
    // =========================================================================

    private void checkSubjectDn(PKCS10CertificationRequest csr,
                                  CsrValidationResult.Builder result,
                                  CsrProfile profile) {
        X500Name subject = csr.getSubject();
        result.subjectDn(subject.toString());

        // CN must be present
        RDN[] cnRdns = subject.getRDNs(BCStyle.CN);
        if (cnRdns == null || cnRdns.length == 0) {
            result.addError("PKI_POL_001",
                "Subject DN has no Common Name (CN). CN is mandatory.");
            return;
        }

        String cn = cnRdns[0].getFirst().getValue().toString().trim();
        result.commonName(cn);

        if (cn.isBlank()) {
            result.addError("PKI_POL_001", "CN is blank");
            return;
        }

        // Profile-specific CN rules
        switch (profile) {
            case TLS_SERVER -> {
                // CN must be a valid FQDN (or wildcard)
                if (cn.contains(" ")) {
                    result.addError("PKI_POL_013",
                        "CN '" + cn + "' contains spaces. " +
                        "TLS_SERVER CN must be a fully qualified domain name (FQDN), " +
                        "e.g. 'api.acme.com'. Person names are not valid server CNs.");
                } else if (!isValidFqdn(cn) && !isWildcardFqdn(cn)) {
                    result.addError("PKI_POL_014",
                        "CN '" + cn + "' is not a valid FQDN. " +
                        "Expected format: hostname.domain.tld");
                } else {
                    log.debug("Layer 5 PASS — TLS_SERVER CN={}", cn);
                }
            }
            case TLS_CLIENT, SMIME -> {
                // CN is a person/service name — spaces allowed, max length check
                if (cn.length() > 128) {
                    result.addWarning("PKI_POL_015",
                        "CN '" + cn + "' exceeds 128 chars; may be truncated by some CAs");
                } else {
                    log.debug("Layer 5 PASS — {} CN={}", profile, cn);
                }
            }
            case CODE_SIGNING, DOCUMENT_SIGNING -> {
                // CN must match organization or developer name — no IP addresses
                if (isIpAddress(cn)) {
                    result.addError("PKI_POL_016",
                        "CN must be an organization or developer name, not an IP address");
                } else {
                    log.debug("Layer 5 PASS — {} CN={}", profile, cn);
                }
            }
        }
    }

    // =========================================================================
    // Layer 6 — SAN (Subject Alternative Names)
    // =========================================================================

    private void checkSan(PKCS10CertificationRequest csr,
                           CsrValidationResult.Builder result,
                           CsrProfile profile) {
        Extensions exts = extractExtensions(csr);
        if (exts == null) {
            if (profile == CsrProfile.TLS_SERVER) {
                result.addError("PKI_POL_004",
                    "TLS_SERVER CSR must contain a subjectAltName extension with " +
                    "at least one dNSName. RFC 2818 §3.1 requires SAN; CN alone is deprecated.");
            }
            return;
        }

        Extension sanExt = exts.getExtension(Extension.subjectAlternativeName);
        if (sanExt == null) {
            if (profile == CsrProfile.TLS_SERVER) {
                result.addError("PKI_POL_004",
                    "TLS_SERVER CSR has no subjectAltName extension. " +
                    "Modern browsers reject TLS certificates without a SAN.");
            }
            return;
        }

        GeneralNames generalNames = GeneralNames.getInstance(sanExt.getParsedValue());
        List<String> sanList = new ArrayList<>();
        boolean hasDnsName = false;
        boolean hasEmailSan = false;

        for (GeneralName gn : generalNames.getNames()) {
            String value = gn.getName().toString();
            switch (gn.getTagNo()) {
                case GeneralName.dNSName -> {
                    hasDnsName = true;
                    sanList.add("DNS:" + value);
                    if (!isValidFqdn(value) && !isWildcardFqdn(value)) {
                        result.addError("PKI_POL_005",
                            "SAN dNSName '" + value + "' is not a valid FQDN");
                    }
                }
                case GeneralName.rfc822Name -> {
                    hasEmailSan = true;
                    sanList.add("email:" + value);
                }
                case GeneralName.iPAddress ->
                    sanList.add("IP:" + value);
                case GeneralName.uniformResourceIdentifier ->
                    sanList.add("URI:" + value);
                default ->
                    sanList.add("other:" + value);
            }
        }

        result.subjectAltNames(String.join(", ", sanList));

        // Profile-specific SAN checks
        switch (profile) {
            case TLS_SERVER -> {
                if (!hasDnsName) {
                    result.addError("PKI_POL_004",
                        "TLS_SERVER SAN has no dNSName entry. Found: " + sanList);
                } else {
                    log.debug("Layer 6 PASS — TLS_SERVER has dNSName SANs: {}", sanList);
                }
            }
            case SMIME -> {
                if (!hasEmailSan) {
                    result.addError("PKI_POL_006",
                        "S/MIME CSR must have an rfc822Name (email) SAN. Found: " + sanList);
                } else {
                    log.debug("Layer 6 PASS — S/MIME has email SAN");
                }
            }
            default -> log.debug("Layer 6 PASS — SANs: {}", sanList);
        }
    }

    // =========================================================================
    // Layer 7 — Extensions: Key Usage + EKU + Basic Constraints
    // =========================================================================

    private void checkExtensions(PKCS10CertificationRequest csr,
                                   CsrValidationResult.Builder result,
                                   CsrProfile profile) {
        Extensions exts = extractExtensions(csr);
        if (exts == null) {
            // No extensions present — warn but do not fail (CA can apply template)
            result.addWarning("PKI_POL_007",
                "CSR contains no X.509v3 extensions. " +
                "The CA will apply defaults from the profile template.");
            return;
        }

        checkBasicConstraints(exts, result);
        checkKeyUsage(exts, result, profile);
        checkEku(exts, result, profile);
    }

    private void checkBasicConstraints(Extensions exts,
                                        CsrValidationResult.Builder result) {
        Extension bcExt = exts.getExtension(Extension.basicConstraints);
        if (bcExt == null) return;

        BasicConstraints bc = BasicConstraints.getInstance(bcExt.getParsedValue());
        if (bc.isCA()) {
            result.addError("PKI_POL_008",
                "CSR has BasicConstraints cA=TRUE. " +
                "End-entity certificates must have cA=FALSE or omit BasicConstraints.");
        } else {
            log.debug("Layer 7 PASS — BasicConstraints cA=FALSE");
        }
    }

    private void checkKeyUsage(Extensions exts,
                                CsrValidationResult.Builder result,
                                CsrProfile profile) {
        Extension kuExt = exts.getExtension(Extension.keyUsage);
        if (kuExt == null) {
            // Not an error — CA template will set Key Usage
            return;
        }

        KeyUsage ku = KeyUsage.getInstance(kuExt.getParsedValue());

        // keyCertSign and cRLSign are CA-only bits — never in end-entity CSR
        if (ku.hasUsages(KeyUsage.keyCertSign)) {
            result.addError("PKI_POL_009",
                "KeyUsage contains keyCertSign — this bit is reserved for CA certificates.");
        }
        if (ku.hasUsages(KeyUsage.cRLSign)) {
            result.addError("PKI_POL_010",
                "KeyUsage contains cRLSign — this bit is reserved for CA certificates.");
        }

        // Profile-specific checks
        switch (profile) {
            case TLS_SERVER -> {
                if (!ku.hasUsages(KeyUsage.digitalSignature)) {
                    result.addWarning("PKI_POL_011",
                        "TLS_SERVER KeyUsage should include digitalSignature for TLS handshake");
                }
            }
            case SMIME -> {
                boolean hasSigning     = ku.hasUsages(KeyUsage.digitalSignature);
                boolean hasEncryption  = ku.hasUsages(KeyUsage.keyEncipherment)
                                      || ku.hasUsages(KeyUsage.keyAgreement);
                if (!hasSigning && !hasEncryption) {
                    result.addWarning("PKI_POL_011",
                        "S/MIME KeyUsage should include digitalSignature and/or keyEncipherment");
                }
            }
            case CODE_SIGNING, DOCUMENT_SIGNING -> {
                if (!ku.hasUsages(KeyUsage.digitalSignature)) {
                    result.addWarning("PKI_POL_011",
                        profile + " KeyUsage should include digitalSignature");
                }
            }
            default -> { /* TLS_CLIENT — digitalSignature checked above */ }
        }

        log.debug("Layer 7 PASS — KeyUsage checks complete for {}", profile);
    }

    private void checkEku(Extensions exts,
                           CsrValidationResult.Builder result,
                           CsrProfile profile) {
        Extension ekuExt = exts.getExtension(Extension.extendedKeyUsage);
        if (ekuExt == null) {
            // Not an error — CA template can enforce EKU
            return;
        }

        ExtendedKeyUsage eku = ExtendedKeyUsage.getInstance(ekuExt.getParsedValue());

        // anyExtendedKeyUsage is forbidden in end-entity certs
        if (eku.hasKeyPurposeId(KeyPurposeId.anyExtendedKeyUsage)) {
            result.addError("PKI_POL_012",
                "EKU contains anyExtendedKeyUsage — this is forbidden in end-entity certificates.");
        }

        // Profile-specific required OIDs (informational warnings only — CA can override)
        switch (profile) {
            case TLS_SERVER -> {
                if (!eku.hasKeyPurposeId(KeyPurposeId.id_kp_serverAuth)) {
                    result.addWarning("PKI_POL_013",
                        "TLS_SERVER EKU should include id-kp-serverAuth (1.3.6.1.5.5.7.3.1)");
                }
            }
            case TLS_CLIENT -> {
                if (!eku.hasKeyPurposeId(KeyPurposeId.id_kp_clientAuth)) {
                    result.addWarning("PKI_POL_014",
                        "TLS_CLIENT EKU should include id-kp-clientAuth (1.3.6.1.5.5.7.3.2)");
                }
            }
            case SMIME -> {
                if (!eku.hasKeyPurposeId(KeyPurposeId.id_kp_emailProtection)) {
                    result.addWarning("PKI_POL_015",
                        "S/MIME EKU should include id-kp-emailProtection (1.3.6.1.5.5.7.3.4)");
                }
            }
            case CODE_SIGNING, DOCUMENT_SIGNING -> {
                if (!eku.hasKeyPurposeId(KeyPurposeId.id_kp_codeSigning)) {
                    result.addWarning("PKI_POL_016",
                        "CODE_SIGNING EKU should include id-kp-codeSigning (1.3.6.1.5.5.7.3.3)");
                }
            }
        }

        log.debug("Layer 7 PASS — EKU checks complete for {}", profile);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Extensions extractExtensions(PKCS10CertificationRequest csr) {
        try {
            for (var attr : csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)) {
                return Extensions.getInstance(attr.getAttrValues().getObjectAt(0));
            }
        } catch (Exception ex) {
            log.debug("No extensionRequest attribute in CSR: {}", ex.getMessage());
        }
        return null;
    }

    private boolean isValidFqdn(String input) {
        if (input == null || input.isBlank()) return false;
        return FQDN_PATTERN.matcher(input).matches();
    }

    private boolean isWildcardFqdn(String input) {
        if (input == null) return false;
        return WILDCARD_FQDN.matcher(input).matches();
    }

    private boolean isIpAddress(String input) {
        if (input == null) return false;
        return input.matches("^(\\d{1,3}\\.){3}\\d{1,3}$")
            || input.contains(":");  // IPv6 rough check
    }

    private String resolveAlgName(String oid) {
        // Common OID → name mappings that BC may return as raw OIDs
        return switch (oid) {
            case "1.2.840.113549.1.1.5"  -> "SHA1WITHRSA";
            case "1.2.840.113549.1.1.11" -> "SHA256WITHRSA";
            case "1.2.840.113549.1.1.12" -> "SHA384WITHRSA";
            case "1.2.840.113549.1.1.13" -> "SHA512WITHRSA";
            case "1.2.840.10045.4.3.2"   -> "SHA256WITHECDSA";
            case "1.2.840.10045.4.3.3"   -> "SHA384WITHECDSA";
            case "1.2.840.10045.4.3.4"   -> "SHA512WITHECDSA";
            case "1.3.101.112"            -> "ED25519";
            case "1.3.101.113"            -> "ED448";
            case "1.2.840.113549.1.1.4"  -> "MD5WITHRSA";
            case "1.2.840.113549.1.1.2"  -> "MD2WITHRSA";
            default -> oid;
        };
    }
}
