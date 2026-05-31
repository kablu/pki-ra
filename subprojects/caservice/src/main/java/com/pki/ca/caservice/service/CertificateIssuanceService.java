package com.pki.ca.caservice.service;

import com.pki.ca.caservice.config.CaProperties;
import com.pki.ca.caservice.exception.CaException;
import com.pki.ca.caservice.model.AlgorithmType;
import com.pki.ca.caservice.model.CertificateRequest;
import com.pki.ca.caservice.model.CertificateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateIssuanceService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final CaProperties caProperties;
    private final CaInitializationService caInitService;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parses the PKCS#10 CSR, signs it with the Intermediate CA, and returns
     * a full certificate response including the PEM chain.
     */
    public CertificateResponse issueDsc(CertificateRequest request) {
        PKCS10CertificationRequest csr = parseCsr(request.getPkcs10());
        log.info("Issuing DSC for subject: {}", csr.getSubject());

        KeyStore ks = caInitService.getKeyStore();

        X509Certificate intermediateCert = loadCertificate(ks, caProperties.getIntermediateAlias());
        PrivateKey intermediateKey = loadPrivateKey(ks, caProperties.getIntermediateAlias());
        X509Certificate rootCert = loadCertificate(ks, caProperties.getRootAlias());

        int validityDays = (request.getValidityDays() != null && request.getValidityDays() > 0)
                ? request.getValidityDays()
                : caProperties.getEndEntityValidityDays();

        X509Certificate issuedCert = signCsr(csr, intermediateCert, intermediateKey,
                validityDays, request.getSubjectAltNames());

        String endEntityPem = toPem(issuedCert);
        String intermediatePem = toPem(intermediateCert);
        String rootPem = toPem(rootCert);

        log.info("DSC issued — serial={}, subject={}, notAfter={}",
                issuedCert.getSerialNumber().toString(16),
                issuedCert.getSubjectX500Principal().getName(),
                issuedCert.getNotAfter());

        return CertificateResponse.builder()
                .certificate(endEntityPem)
                .intermediateCertificate(intermediatePem)
                .rootCertificate(rootPem)
                .certificateChain(endEntityPem + intermediatePem + rootPem)
                .serialNumber(issuedCert.getSerialNumber().toString(16).toUpperCase())
                .signatureAlgorithm(issuedCert.getSigAlgName())
                .subject(issuedCert.getSubjectX500Principal().getName())
                .notBefore(ISO.format(issuedCert.getNotBefore().toInstant().atOffset(ZoneOffset.UTC)))
                .notAfter(ISO.format(issuedCert.getNotAfter().toInstant().atOffset(ZoneOffset.UTC)))
                .build();
    }

    // -------------------------------------------------------------------------
    // CSR parsing — auto-detects PEM vs base64-DER
    // -------------------------------------------------------------------------

    private PKCS10CertificationRequest parseCsr(String input) {
        String trimmed = input.strip();
        try {
            if (trimmed.startsWith("-----")) {
                try (PEMParser parser = new PEMParser(new StringReader(trimmed))) {
                    Object obj = parser.readObject();
                    if (obj instanceof PKCS10CertificationRequest csr) return csr;
                    throw new CaException("PEM block does not contain a PKCS#10 CSR");
                }
            } else {
                byte[] der = Base64.getMimeDecoder().decode(trimmed.replaceAll("\\s+", ""));
                return new PKCS10CertificationRequest(der);
            }
        } catch (IOException ex) {
            throw new CaException("Failed to parse PKCS#10 CSR: " + ex.getMessage(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Certificate signing
    // -------------------------------------------------------------------------

    private X509Certificate signCsr(
            PKCS10CertificationRequest csr,
            X509Certificate issuerCert,
            PrivateKey issuerKey,
            int validityDays,
            String extraSans) {

        try {
            X500Name issuerDn = new X500Name(issuerCert.getSubjectX500Principal().getName());
            X500Name subjectDn = csr.getSubject();
            BigInteger serial = new BigInteger(128, new SecureRandom());
            Date notBefore = Date.from(Instant.now().minus(1, ChronoUnit.MINUTES));
            Date notAfter  = Date.from(Instant.now().plus(validityDays, ChronoUnit.DAYS));

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuerDn, serial, notBefore, notAfter, subjectDn,
                    csr.getSubjectPublicKeyInfo());

            JcaX509ExtensionUtils extUtils = extensionUtils();

            // End-entity: not a CA
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

            // DSC key usages: digital signature + non-repudiation
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));

            // Extended key usages: email protection (S/MIME signing)
            builder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(new KeyPurposeId[]{
                            KeyPurposeId.id_kp_emailProtection
                    }));

            // SKI / AKI
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    extUtils.createSubjectKeyIdentifier(csr.getSubjectPublicKeyInfo()));
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(issuerCert));

            // Copy SANs from CSR (if present)
            copySansFromCsr(builder, csr);

            // Caller-supplied extra SANs
            if (extraSans != null && !extraSans.isBlank()) {
                addExtraSans(builder, extraSans);
            }

            // CRL distribution point (if configured)
            if (!caProperties.getCrlDistributionPoint().isBlank()) {
                addCdp(builder, caProperties.getCrlDistributionPoint());
            }

            AlgorithmType issuerAlgo = caInitService.detectAlgorithm(issuerCert);
            ContentSigner signer = new JcaContentSignerBuilder(caInitService.signingAlgorithm(issuerAlgo))
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(issuerKey);

            X509CertificateHolder holder = builder.build(signer);
            return new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(holder);

        } catch (Exception ex) {
            throw new CaException("Certificate signing failed: " + ex.getMessage(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // SAN helpers
    // -------------------------------------------------------------------------

    private void copySansFromCsr(X509v3CertificateBuilder builder, PKCS10CertificationRequest csr) {
        try {
            Extensions csrExtensions = null;
            for (var attr : csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)) {
                csrExtensions = Extensions.getInstance(attr.getAttrValues().getObjectAt(0));
            }
            if (csrExtensions == null) return;

            Extension san = csrExtensions.getExtension(Extension.subjectAlternativeName);
            if (san != null) builder.addExtension(san);
        } catch (Exception ex) {
            log.warn("Could not copy SANs from CSR: {}", ex.getMessage());
        }
    }

    private void addExtraSans(X509v3CertificateBuilder builder, String sans) {
        try {
            List<GeneralName> names = new ArrayList<>();
            for (String part : sans.split(",")) {
                String val = part.strip();
                if (val.contains("@")) {
                    names.add(new GeneralName(GeneralName.rfc822Name, val));
                } else if (val.startsWith("dns:")) {
                    names.add(new GeneralName(GeneralName.dNSName, val.substring(4).strip()));
                } else if (val.startsWith("ip:")) {
                    names.add(new GeneralName(GeneralName.iPAddress, val.substring(3).strip()));
                } else {
                    names.add(new GeneralName(GeneralName.rfc822Name, val));
                }
            }
            if (!names.isEmpty()) {
                builder.addExtension(Extension.subjectAlternativeName, false,
                        new GeneralNames(names.toArray(new GeneralName[0])));
            }
        } catch (Exception ex) {
            log.warn("Could not add extra SANs: {}", ex.getMessage());
        }
    }

    private void addCdp(X509v3CertificateBuilder builder, String cdpUrl) {
        try {
            DistributionPoint dp = new DistributionPoint(
                    new DistributionPointName(
                            new GeneralNames(new GeneralName(GeneralName.uniformResourceIdentifier, cdpUrl))),
                    null, null);
            builder.addExtension(Extension.cRLDistributionPoints, false,
                    new CRLDistPoint(new DistributionPoint[]{dp}));
        } catch (Exception ex) {
            log.warn("Could not add CDP extension: {}", ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // PEM encoding
    // -------------------------------------------------------------------------

    public String toPem(X509Certificate cert) {
        try (StringWriter sw = new StringWriter(); JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(cert);
            writer.flush();
            return sw.toString();
        } catch (IOException ex) {
            throw new CaException("Failed to encode certificate to PEM: " + ex.getMessage(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Keystore helpers
    // -------------------------------------------------------------------------

    private X509Certificate loadCertificate(KeyStore ks, String alias) {
        try {
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            if (cert == null) throw new CaException("No certificate found for alias: " + alias);
            return cert;
        } catch (CaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CaException("Cannot load certificate [" + alias + "]: " + ex.getMessage(), ex);
        }
    }

    private PrivateKey loadPrivateKey(KeyStore ks, String alias) {
        try {
            PrivateKey key = (PrivateKey) ks.getKey(alias,
                    caProperties.getKeystorePassword().toCharArray());
            if (key == null) throw new CaException("No private key found for alias: " + alias);
            return key;
        } catch (CaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CaException("Cannot load private key [" + alias + "]: " + ex.getMessage(), ex);
        }
    }

    private JcaX509ExtensionUtils extensionUtils() {
        try {
            return new JcaX509ExtensionUtils();
        } catch (NoSuchAlgorithmException ex) {
            throw new CaException("Extension utils init failed", ex);
        }
    }
}
