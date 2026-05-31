package com.pki.ca.caservice.service;

import com.pki.ca.caservice.config.CaProperties;
import com.pki.ca.caservice.exception.CaException;
import com.pki.ca.caservice.model.AlgorithmType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaInitializationService {

    private final CaProperties caProperties;
    private final KeyStoreService keyStoreService;

    private KeyStore keyStore;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        log.info("Initializing CA hierarchy...");
        keyStore = keyStoreService.loadOrCreate();

        boolean rootMissing = !keyStoreService.hasAlias(keyStore, caProperties.getRootAlias());
        boolean intermediateMissing = !keyStoreService.hasAlias(keyStore, caProperties.getIntermediateAlias());

        if (rootMissing) {
            log.info("Root CA not found — generating new Root CA ({})...", caProperties.getDefaultAlgorithm());
            generateRootCa();
        } else {
            log.info("Root CA already present in keystore.");
        }

        if (intermediateMissing) {
            log.info("Intermediate CA not found — generating new Intermediate CA ({})...", caProperties.getDefaultAlgorithm());
            generateIntermediateCa();
        } else {
            log.info("Intermediate CA already present in keystore.");
        }
    }

    // -------------------------------------------------------------------------
    // Root CA bootstrap
    // -------------------------------------------------------------------------

    private void generateRootCa() {
        try {
            AlgorithmType algo = resolveAlgorithm();
            KeyPair rootKeyPair = generateKeyPair(algo);

            X500Name rootDn = new X500Name(caProperties.getRootDn());
            BigInteger serial = newSerial();
            Date notBefore = Date.from(Instant.now().minus(1, ChronoUnit.MINUTES));
            Date notAfter = Date.from(Instant.now().plus(caProperties.getRootValidityYears() * 365L, ChronoUnit.DAYS));

            JcaX509ExtensionUtils extUtils = extensionUtils();

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    rootDn, serial, notBefore, notAfter, rootDn, rootKeyPair.getPublic());

            // CA:true, unlimited path length
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    extUtils.createSubjectKeyIdentifier(rootKeyPair.getPublic()));

            ContentSigner signer = signer(algo, rootKeyPair);
            X509CertificateHolder holder = builder.build(signer);
            X509Certificate rootCert = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(holder);

            keyStoreService.storeKeyEntry(keyStore, caProperties.getRootAlias(),
                    rootKeyPair.getPrivate(), new X509Certificate[]{rootCert});
            keyStoreService.save(keyStore);

            log.info("Root CA generated. Serial={}, Subject={}", serial.toString(16), caProperties.getRootDn());
        } catch (Exception ex) {
            throw new CaException("Root CA generation failed: " + ex.getMessage(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Intermediate CA bootstrap
    // -------------------------------------------------------------------------

    private void generateIntermediateCa() {
        try {
            AlgorithmType algo = resolveAlgorithm();
            KeyPair intermediateKeyPair = generateKeyPair(algo);

            X509Certificate rootCert = keyStoreService.getCertificate(keyStore, caProperties.getRootAlias());
            var rootPrivateKey = keyStoreService.getPrivateKey(keyStore, caProperties.getRootAlias());

            X500Name issuerDn = new X500Name(rootCert.getSubjectX500Principal().getName());
            X500Name subjectDn = new X500Name(caProperties.getIntermediateDn());
            BigInteger serial = newSerial();
            Date notBefore = Date.from(Instant.now().minus(1, ChronoUnit.MINUTES));
            Date notAfter = Date.from(Instant.now().plus(caProperties.getIntermediateValidityYears() * 365L, ChronoUnit.DAYS));

            JcaX509ExtensionUtils extUtils = extensionUtils();

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuerDn, serial, notBefore, notAfter, subjectDn, intermediateKeyPair.getPublic());

            // CA:true, pathLenConstraint=0 means this intermediate can only issue end-entity certs
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    extUtils.createSubjectKeyIdentifier(intermediateKeyPair.getPublic()));
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(rootCert));

            // Root CA signs the Intermediate CA certificate
            AlgorithmType rootAlgo = detectAlgorithm(rootCert);
            ContentSigner signer = new JcaContentSignerBuilder(signingAlgorithm(rootAlgo))
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(rootPrivateKey);

            X509CertificateHolder holder = builder.build(signer);
            X509Certificate intermediateCert = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(holder);

            // Store chain: intermediate → root
            keyStoreService.storeKeyEntry(keyStore, caProperties.getIntermediateAlias(),
                    intermediateKeyPair.getPrivate(),
                    new X509Certificate[]{intermediateCert, rootCert});
            keyStoreService.save(keyStore);

            log.info("Intermediate CA generated. Serial={}, Subject={}", serial.toString(16), caProperties.getIntermediateDn());
        } catch (Exception ex) {
            throw new CaException("Intermediate CA generation failed: " + ex.getMessage(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Package-internal accessors used by CertificateIssuanceService
    // -------------------------------------------------------------------------

    public KeyStore getKeyStore() {
        if (keyStore == null) {
            throw new CaException("CA not yet initialized — keystore is null");
        }
        return keyStore;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    KeyPair generateKeyPair(AlgorithmType algo) {
        try {
            return switch (algo) {
                case RSA -> {
                    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
                    kpg.initialize(caProperties.getRsaKeySize(), new SecureRandom());
                    yield kpg.generateKeyPair();
                }
                case ECDSA -> {
                    KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
                    kpg.initialize(ECNamedCurveTable.getParameterSpec(caProperties.getEcCurve()), new SecureRandom());
                    yield kpg.generateKeyPair();
                }
            };
        } catch (NoSuchAlgorithmException | NoSuchProviderException | java.security.InvalidAlgorithmParameterException ex) {
            throw new CaException("Key pair generation failed: " + ex.getMessage(), ex);
        }
    }

    String signingAlgorithm(AlgorithmType algo) {
        return switch (algo) {
            case RSA -> "SHA256withRSA";
            case ECDSA -> "SHA256withECDSA";
        };
    }

    private ContentSigner signer(AlgorithmType algo, KeyPair kp) throws OperatorCreationException {
        return new JcaContentSignerBuilder(signingAlgorithm(algo))
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(kp.getPrivate());
    }

    AlgorithmType detectAlgorithm(X509Certificate cert) {
        String alg = cert.getPublicKey().getAlgorithm();
        return alg.startsWith("EC") ? AlgorithmType.ECDSA : AlgorithmType.RSA;
    }

    private AlgorithmType resolveAlgorithm() {
        return AlgorithmType.valueOf(caProperties.getDefaultAlgorithm().toUpperCase());
    }

    private BigInteger newSerial() {
        return new BigInteger(128, new SecureRandom());
    }

    private JcaX509ExtensionUtils extensionUtils() {
        try {
            return new JcaX509ExtensionUtils();
        } catch (NoSuchAlgorithmException ex) {
            throw new CaException("Extension utils init failed: " + ex.getMessage(), ex);
        }
    }
}
