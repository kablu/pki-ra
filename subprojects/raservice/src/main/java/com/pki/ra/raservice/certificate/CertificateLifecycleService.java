package com.pki.ra.raservice.certificate;

import com.pki.ra.common.certificate.*;
import com.pki.ra.common.certificate.CaClient.CaSigningResult;
import com.pki.ra.common.certificate.dto.CertificateDto;
import com.pki.ra.common.certificate.dto.CertificateRequestDto;
import com.pki.ra.common.certificate.dto.CertificateRequestDto.ApprovalDto;
import com.pki.ra.common.exception.ExceptionFactory;
import com.pki.ra.common.model.*;
import com.pki.ra.common.model.Certificate;
import com.pki.ra.common.model.Certificate.CertificateStatus;
import com.pki.ra.common.model.CertificateRequest.RequestStatus;
import com.pki.ra.common.model.CertificateRequestApproval.ApprovalDecision;
import com.pki.ra.common.user.UserRepository;
import com.pki.ra.common.util.AuditLogService;
import com.pki.ra.raservice.error.RaErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.openssl.PEMParser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringReader;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CertificateLifecycleService {

    private final CertificateRequestRepository requestRepo;
    private final CertificateRepository certRepo;
    private final CertificateRequestApprovalRepository approvalRepo;
    private final UserRepository userRepo;
    private final CaClient caClient;
    private final AuditLogService auditLogService;
    private final ExceptionFactory exceptionFactory;

    public CertificateLifecycleService(CertificateRequestRepository requestRepo,
                                        CertificateRepository certRepo,
                                        CertificateRequestApprovalRepository approvalRepo,
                                        UserRepository userRepo,
                                        CaClient caClient,
                                        AuditLogService auditLogService,
                                        ExceptionFactory exceptionFactory) {
        this.requestRepo = requestRepo;
        this.certRepo = certRepo;
        this.approvalRepo = approvalRepo;
        this.userRepo = userRepo;
        this.caClient = caClient;
        this.auditLogService = auditLogService;
        this.exceptionFactory = exceptionFactory;
    }

    // =========================================================================
    // Submit CSR
    // =========================================================================

    @Transactional
    public CertificateRequestDto submitRequest(String csrPem, Integer validityDays,
                                                String sans, String username, String ipAddress) {
        User requester = findUserByUsername(username);

        CsrInfo csrInfo = parseCsr(csrPem);

        CertificateRequest request = CertificateRequest.builder()
                .csrPem(csrPem)
                .subjectDn(csrInfo.subjectDn())
                .keyAlgorithm(csrInfo.keyAlgorithm())
                .keySize(csrInfo.keySize())
                .subjectAltNames(csrInfo.sans() != null ? csrInfo.sans() : sans)
                .requestedValidityDays(validityDays)
                .status(RequestStatus.PENDING)
                .requester(requester)
                .build();

        request = requestRepo.save(request);

        auditLogService.logSuccess(username, "CSR_SUBMIT",
                String.valueOf(request.getId()),
                "CSR submitted: " + csrInfo.subjectDn(), ipAddress);

        return toDto(request);
    }

    // =========================================================================
    // Approve / Reject
    // =========================================================================

    @Transactional
    public CertificateRequestDto approveRequest(Long requestId, String comment,
                                                 String approverUsername, String ipAddress) {
        CertificateRequest request = findRequest(requestId);
        validatePendingStatus(request);

        User approver = findUserByUsername(approverUsername);

        CertificateRequestApproval approval = CertificateRequestApproval.builder()
                .request(request)
                .approver(approver)
                .decision(ApprovalDecision.APPROVED)
                .comment(comment)
                .build();
        approvalRepo.save(approval);

        request.setStatus(RequestStatus.APPROVED);
        request = requestRepo.save(request);

        auditLogService.logSuccess(approverUsername, "CSR_APPROVE",
                String.valueOf(requestId), "Request approved", ipAddress);

        // Auto-issue after approval
        return issueCertificate(request, approverUsername, ipAddress);
    }

    @Transactional
    public CertificateRequestDto rejectRequest(Long requestId, String reason,
                                                String approverUsername, String ipAddress) {
        CertificateRequest request = findRequest(requestId);
        validatePendingStatus(request);

        User approver = findUserByUsername(approverUsername);

        CertificateRequestApproval approval = CertificateRequestApproval.builder()
                .request(request)
                .approver(approver)
                .decision(ApprovalDecision.REJECTED)
                .comment(reason)
                .build();
        approvalRepo.save(approval);

        request.setStatus(RequestStatus.REJECTED);
        request.setStatusReason(reason);
        requestRepo.save(request);

        auditLogService.logSuccess(approverUsername, "CSR_REJECT",
                String.valueOf(requestId), "Request rejected: " + reason, ipAddress);

        return toDto(request);
    }

    // =========================================================================
    // Issue Certificate (after approval)
    // =========================================================================

    private CertificateRequestDto issueCertificate(CertificateRequest request,
                                                    String actorUsername, String ipAddress) {
        try {
            CaSigningResult result = caClient.sign(
                    request.getCsrPem(),
                    request.getRequestedValidityDays(),
                    request.getSubjectAltNames());

            String fingerprint = computeSha256Fingerprint(result.certificatePem());

            Certificate cert = Certificate.builder()
                    .serialNumber(result.serialNumber())
                    .subjectDn(result.subject())
                    .issuerDn(result.issuer())
                    .notBefore(Instant.parse(result.notBefore()))
                    .notAfter(Instant.parse(result.notAfter()))
                    .certificatePem(result.certificatePem())
                    .certificateChain(result.certificateChain())
                    .keyAlgorithm(request.getKeyAlgorithm())
                    .keySize(request.getKeySize())
                    .signatureAlgorithm(result.signatureAlgorithm())
                    .status(CertificateStatus.ACTIVE)
                    .fingerprintSha256(fingerprint)
                    .request(request)
                    .build();

            cert = certRepo.save(cert);

            request.setStatus(RequestStatus.ISSUED);
            request.setCertificate(cert);
            request = requestRepo.save(request);

            auditLogService.logSuccess(actorUsername, "CERT_ISSUE",
                    result.serialNumber(),
                    "Certificate issued: " + result.subject(), ipAddress);

            return toDto(request);

        } catch (Exception e) {
            request.setStatus(RequestStatus.FAILED);
            request.setStatusReason(e.getMessage());
            requestRepo.save(request);

            auditLogService.logFailure(actorUsername, "CERT_ISSUE",
                    String.valueOf(request.getId()),
                    "Issuance failed: " + e.getMessage(), ipAddress);

            throw exceptionFactory.createAndLog(log, RaErrorCode.CERT_GENERATION_FAIL, e,
                    request.getSubjectDn());
        }
    }

    // =========================================================================
    // Query
    // =========================================================================

    @Transactional(readOnly = true)
    public CertificateRequestDto getRequest(Long id) {
        return toDto(findRequest(id));
    }

    @Transactional(readOnly = true)
    public Page<CertificateRequestDto> listRequests(RequestStatus status, Pageable pageable) {
        Page<CertificateRequest> page = (status != null)
                ? requestRepo.findByStatus(status, pageable)
                : requestRepo.findAll(pageable);
        return page.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public CertificateDto getCertificateBySerial(String serialNumber) {
        Certificate cert = certRepo.findBySerialNumber(serialNumber)
                .orElseThrow(() -> exceptionFactory.create(RaErrorCode.CERT_NOT_FOUND, serialNumber));
        return toCertDto(cert);
    }

    @Transactional(readOnly = true)
    public String downloadCertificate(String serialNumber, String format) {
        Certificate cert = certRepo.findBySerialNumber(serialNumber)
                .orElseThrow(() -> exceptionFactory.create(RaErrorCode.CERT_NOT_FOUND, serialNumber));

        return switch (format.toLowerCase()) {
            case "pem" -> cert.getCertificatePem();
            case "chain" -> cert.getCertificateChain();
            default -> cert.getCertificatePem();
        };
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private User findUserByUsername(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> exceptionFactory.create(RaErrorCode.AUTH_FAILED, username));
    }

    private CertificateRequest findRequest(Long id) {
        return requestRepo.findById(id)
                .orElseThrow(() -> exceptionFactory.create(RaErrorCode.CERT_NOT_FOUND, String.valueOf(id)));
    }

    private void validatePendingStatus(CertificateRequest request) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw exceptionFactory.create(RaErrorCode.VALIDATION_INVALID_CSR,
                    "Request is not in PENDING status: " + request.getStatus());
        }
    }

    private record CsrInfo(String subjectDn, String keyAlgorithm, int keySize, String sans) {}

    private CsrInfo parseCsr(String csrPem) {
        try (PEMParser parser = new PEMParser(new StringReader(csrPem))) {
            Object parsed = parser.readObject();
            if (!(parsed instanceof PKCS10CertificationRequest csr)) {
                throw exceptionFactory.create(RaErrorCode.VALIDATION_INVALID_CSR, "Invalid PKCS#10 format");
            }

            X500Name subject = csr.getSubject();
            var keyInfo = csr.getSubjectPublicKeyInfo();
            String algorithm = keyInfo.getAlgorithm().getAlgorithm().getId().startsWith("1.2.840.10045")
                    ? "ECDSA" : "RSA";
            int keySize = estimateKeySize(keyInfo.getEncoded().length, algorithm);

            String sans = extractSansFromCsr(csr);

            return new CsrInfo(subject.toString(), algorithm, keySize, sans);
        } catch (Exception e) {
            if (e instanceof com.pki.ra.common.exception.AppException) throw (RuntimeException) e;
            throw exceptionFactory.createAndLog(log, RaErrorCode.VALIDATION_INVALID_CSR, e, csrPem.substring(0, Math.min(50, csrPem.length())));
        }
    }

    private int estimateKeySize(int encodedLength, String algorithm) {
        if ("ECDSA".equals(algorithm)) {
            if (encodedLength <= 91) return 256;
            if (encodedLength <= 120) return 384;
            return 521;
        }
        if (encodedLength <= 162) return 1024;
        if (encodedLength <= 294) return 2048;
        if (encodedLength <= 550) return 4096;
        return 4096;
    }

    private String extractSansFromCsr(PKCS10CertificationRequest csr) {
        try {
            var attributes = csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest);
            if (attributes == null || attributes.length == 0) return null;

            Extensions extensions = Extensions.getInstance(attributes[0].getAttrValues().getObjectAt(0));
            Extension sanExt = extensions.getExtension(Extension.subjectAlternativeName);
            if (sanExt == null) return null;

            GeneralNames names = GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName);
            StringBuilder sb = new StringBuilder();
            for (GeneralName name : names.getNames()) {
                if (!sb.isEmpty()) sb.append(",");
                sb.append(name.getName().toString());
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String computeSha256Fingerprint(String pem) {
        try {
            String base64 = pem
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(der);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02X", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // =========================================================================
    // DTO mappers
    // =========================================================================

    private CertificateRequestDto toDto(CertificateRequest r) {
        List<CertificateRequestApproval> approvals = approvalRepo.findByRequestId(r.getId());

        return CertificateRequestDto.builder()
                .id(r.getId())
                .subjectDn(r.getSubjectDn())
                .keyAlgorithm(r.getKeyAlgorithm())
                .keySize(r.getKeySize())
                .subjectAltNames(r.getSubjectAltNames())
                .requestedValidityDays(r.getRequestedValidityDays())
                .status(r.getStatus().name())
                .statusReason(r.getStatusReason())
                .requesterUsername(r.getRequester().getUsername())
                .certificateId(r.getCertificate() != null ? r.getCertificate().getId() : null)
                .certificateSerial(r.getCertificate() != null ? r.getCertificate().getSerialNumber() : null)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .approvals(approvals.stream().map(a -> ApprovalDto.builder()
                        .approverUsername(a.getApprover().getUsername())
                        .decision(a.getDecision().name())
                        .comment(a.getComment())
                        .createdAt(a.getCreatedAt())
                        .build()).collect(Collectors.toList()))
                .build();
    }

    private CertificateDto toCertDto(Certificate c) {
        return CertificateDto.builder()
                .id(c.getId())
                .serialNumber(c.getSerialNumber())
                .subjectDn(c.getSubjectDn())
                .issuerDn(c.getIssuerDn())
                .notBefore(c.getNotBefore())
                .notAfter(c.getNotAfter())
                .keyAlgorithm(c.getKeyAlgorithm())
                .keySize(c.getKeySize())
                .signatureAlgorithm(c.getSignatureAlgorithm())
                .status(c.getStatus().name())
                .fingerprintSha256(c.getFingerprintSha256())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
