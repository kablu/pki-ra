package com.pki.ca.caservice.controller;

import com.pki.ca.caservice.config.CaProperties;
import com.pki.ca.caservice.model.CertificateRequest;
import com.pki.ca.caservice.model.CertificateResponse;
import com.pki.ca.caservice.service.CaInitializationService;
import com.pki.ca.caservice.service.CertificateIssuanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

@Slf4j
@RestController
@RequestMapping("/api/ca")
@RequiredArgsConstructor
@Tag(name = "Certificate Authority", description = "PKI CA operations — CSR signing and CA certificate retrieval")
public class CaController {

    private final CertificateIssuanceService issuanceService;
    private final CaInitializationService caInitService;
    private final CaProperties caProperties;

    // =========================================================================
    // DSC issuance
    // =========================================================================

    @Operation(
        summary = "Sign a PKCS#10 CSR and issue a DSC",
        description = """
            Accepts a PKCS#10 Certificate Signing Request (PEM or base64-DER) and signs
            it with the Intermediate CA to produce a Digital Signature Certificate (DSC).
            Both RSA and ECDSA CSRs are accepted. The response includes the issued certificate,
            the full chain (end-entity → intermediate → root), and metadata.
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "DSC issued successfully",
                content = @Content(schema = @Schema(implementation = CertificateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or malformed CSR"),
            @ApiResponse(responseCode = "422", description = "CA operation failed")
        }
    )
    @PostMapping("/sign")
    public ResponseEntity<CertificateResponse> signCsr(
            @Valid @RequestBody CertificateRequest request) {
        log.info("Received CSR signing request");
        CertificateResponse response = issuanceService.issueDsc(request);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // CA certificate endpoints
    // =========================================================================

    @Operation(summary = "Download Root CA certificate in PEM format")
    @GetMapping(value = "/root-cert", produces = "application/x-pem-file")
    public ResponseEntity<String> getRootCert() {
        String pem = getCertPem(caProperties.getRootAlias());
        return pemResponse(pem, "root-ca.pem");
    }

    @Operation(summary = "Download Intermediate CA certificate in PEM format")
    @GetMapping(value = "/intermediate-cert", produces = "application/x-pem-file")
    public ResponseEntity<String> getIntermediateCert() {
        String pem = getCertPem(caProperties.getIntermediateAlias());
        return pemResponse(pem, "intermediate-ca.pem");
    }

    @Operation(
        summary = "Download full CA certificate chain (intermediate + root) in PEM format",
        description = "Returns intermediate CA certificate followed by root CA certificate, both PEM-encoded."
    )
    @GetMapping(value = "/chain", produces = "application/x-pem-file")
    public ResponseEntity<String> getChain() {
        String intermediate = getCertPem(caProperties.getIntermediateAlias());
        String root = getCertPem(caProperties.getRootAlias());
        return pemResponse(intermediate + root, "ca-chain.pem");
    }

    @Operation(summary = "Retrieve CA status and algorithm info")
    @GetMapping("/status")
    public ResponseEntity<CaStatusResponse> getStatus() {
        KeyStore ks = caInitService.getKeyStore();
        boolean rootReady = isAliasPresent(ks, caProperties.getRootAlias());
        boolean intermediateReady = isAliasPresent(ks, caProperties.getIntermediateAlias());

        String rootSubject = rootReady ? getSubject(ks, caProperties.getRootAlias()) : "not initialized";
        String intermediateSubject = intermediateReady
                ? getSubject(ks, caProperties.getIntermediateAlias())
                : "not initialized";

        return ResponseEntity.ok(new CaStatusResponse(
                rootReady && intermediateReady,
                rootSubject,
                intermediateSubject,
                caProperties.getDefaultAlgorithm(),
                caProperties.getEndEntityValidityDays()
        ));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String getCertPem(String alias) {
        KeyStore ks = caInitService.getKeyStore();
        try {
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            if (cert == null) throw new IllegalStateException("Certificate not found: " + alias);
            return issuanceService.toPem(cert);
        } catch (Exception ex) {
            throw new com.pki.ca.caservice.exception.CaException(
                    "Cannot retrieve certificate [" + alias + "]: " + ex.getMessage(), ex);
        }
    }

    private ResponseEntity<String> pemResponse(String pem, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/x-pem-file"))
                .body(pem);
    }

    private boolean isAliasPresent(KeyStore ks, String alias) {
        try {
            return ks.isKeyEntry(alias);
        } catch (Exception ex) {
            return false;
        }
    }

    private String getSubject(KeyStore ks, String alias) {
        try {
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            return cert != null ? cert.getSubjectX500Principal().getName() : "unknown";
        } catch (Exception ex) {
            return "error";
        }
    }

    // =========================================================================
    // Status response record
    // =========================================================================

    public record CaStatusResponse(
            boolean initialized,
            String rootCaSubject,
            String intermediateCaSubject,
            String defaultAlgorithm,
            int defaultEndEntityValidityDays
    ) {}
}
