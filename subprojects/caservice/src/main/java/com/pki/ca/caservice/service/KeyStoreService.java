package com.pki.ca.caservice.service;

import com.pki.ca.caservice.config.CaProperties;
import com.pki.ca.caservice.exception.CaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyStoreService {

    private final CaProperties caProperties;

    /** Loads or creates the PKCS#12 keystore from disk. */
    public KeyStore loadOrCreate() {
        Path ksPath = Path.of(caProperties.getKeystorePath());
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME); //NOSONAR — NoSuchProviderException handled below
            if (Files.exists(ksPath)) {
                log.info("Loading existing CA keystore from {}", ksPath.toAbsolutePath());
                try (FileInputStream fis = new FileInputStream(ksPath.toFile())) {
                    ks.load(fis, caProperties.getKeystorePassword().toCharArray());
                }
            } else {
                log.info("No keystore found — creating new empty keystore at {}", ksPath.toAbsolutePath());
                ks.load(null, caProperties.getKeystorePassword().toCharArray());
            }
            return ks;
        } catch (KeyStoreException | NoSuchProviderException | IOException
                | NoSuchAlgorithmException | CertificateException ex) {
            throw new CaException("Failed to load/create keystore: " + ex.getMessage(), ex);
        }
    }

    /** Persists the keystore to disk. */
    public void save(KeyStore ks) {
        Path ksPath = Path.of(caProperties.getKeystorePath());
        try (FileOutputStream fos = new FileOutputStream(ksPath.toFile())) {
            ks.store(fos, caProperties.getKeystorePassword().toCharArray());
            log.info("CA keystore saved to {}", ksPath.toAbsolutePath());
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException ex) {
            throw new CaException("Failed to save keystore: " + ex.getMessage(), ex);
        }
    }

    /** Stores a private key + certificate chain under the given alias. */
    public void storeKeyEntry(KeyStore ks, String alias, PrivateKey key, Certificate[] chain) {
        try {
            ks.setKeyEntry(alias, key, caProperties.getKeystorePassword().toCharArray(), chain);
        } catch (KeyStoreException ex) {
            throw new CaException("Failed to store key entry [" + alias + "]: " + ex.getMessage(), ex);
        }
    }

    /** Retrieves a private key by alias. */
    public PrivateKey getPrivateKey(KeyStore ks, String alias) {
        try {
            return (PrivateKey) ks.getKey(alias, caProperties.getKeystorePassword().toCharArray());
        } catch (Exception ex) {
            throw new CaException("Failed to retrieve private key [" + alias + "]: " + ex.getMessage(), ex);
        }
    }

    /** Retrieves the leaf (end-entity) certificate from a key entry. */
    public X509Certificate getCertificate(KeyStore ks, String alias) {
        try {
            return (X509Certificate) ks.getCertificate(alias);
        } catch (KeyStoreException ex) {
            throw new CaException("Failed to retrieve certificate [" + alias + "]: " + ex.getMessage(), ex);
        }
    }

    /** Returns true when the given alias already has a key entry in the keystore. */
    public boolean hasAlias(KeyStore ks, String alias) {
        try {
            return ks.isKeyEntry(alias);
        } catch (KeyStoreException ex) {
            throw new CaException("Failed to check alias [" + alias + "]: " + ex.getMessage(), ex);
        }
    }
}
