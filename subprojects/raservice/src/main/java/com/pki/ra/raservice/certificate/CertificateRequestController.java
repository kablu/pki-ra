package com.pki.ra.raservice.certificate;

import com.pki.ra.common.certificate.dto.ApprovalRequest;
import com.pki.ra.common.certificate.dto.CertificateDto;
import com.pki.ra.common.certificate.dto.CertificateRequestDto;
import com.pki.ra.common.certificate.dto.CsrSubmitRequest;
import com.pki.ra.common.model.CertificateRequest.RequestStatus;
import com.pki.ra.common.user.service.UserLookupService;
import com.pki.ra.common.util.AuditLogService;
import com.pki.ra.common.web.AbstractSecuredController;
import com.pki.ra.common.web.AuditContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ra")
@Tag(name = "Certificate Lifecycle", description = "CSR submission, approval, issuance, and download")
public class CertificateRequestController extends AbstractSecuredController {

    private final CertificateLifecycleService lifecycleService;

    public CertificateRequestController(AuditLogService auditLogService,
                                         UserLookupService userLookupService,
                                         CertificateLifecycleService lifecycleService) {
        super(auditLogService, userLookupService);
        this.lifecycleService = lifecycleService;
    }

    // =========================================================================
    // CSR Submission
    // =========================================================================

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a Certificate Signing Request (CSR)")
    public CertificateRequestDto submitRequest(@Valid @RequestBody CsrSubmitRequest body,
                                                HttpServletRequest httpRequest) {
        AuditContext ctx = resolveAuditContext(httpRequest);
        return lifecycleService.submitRequest(
                body.getPkcs10(),
                body.getValidityDays(),
                body.getSubjectAltNames(),
                ctx.username(), ctx.ip());
    }

    // =========================================================================
    // Request queries
    // =========================================================================

    @GetMapping("/requests/{id}")
    @Operation(summary = "Get certificate request by ID")
    public CertificateRequestDto getRequest(@PathVariable Long id) {
        return lifecycleService.getRequest(id);
    }

    @GetMapping("/requests")
    @Operation(summary = "List certificate requests with optional status filter")
    public Page<CertificateRequestDto> listRequests(
            @RequestParam(required = false) RequestStatus status,
            Pageable pageable) {
        return lifecycleService.listRequests(status, pageable);
    }

    // =========================================================================
    // Approval / Rejection
    // =========================================================================

    @PostMapping("/requests/{id}/approve")
    @Operation(summary = "Approve a pending certificate request")
    public CertificateRequestDto approveRequest(@PathVariable Long id,
                                                 @RequestBody(required = false) ApprovalRequest body,
                                                 HttpServletRequest httpRequest) {
        AuditContext ctx = resolveAuditContext(httpRequest);
        String comment = (body != null) ? body.getComment() : null;
        return lifecycleService.approveRequest(id, comment, ctx.username(), ctx.ip());
    }

    @PostMapping("/requests/{id}/reject")
    @Operation(summary = "Reject a pending certificate request")
    public CertificateRequestDto rejectRequest(@PathVariable Long id,
                                                @RequestBody(required = false) ApprovalRequest body,
                                                HttpServletRequest httpRequest) {
        AuditContext ctx = resolveAuditContext(httpRequest);
        String reason = (body != null) ? body.getComment() : "No reason provided";
        return lifecycleService.rejectRequest(id, reason, ctx.username(), ctx.ip());
    }

    // =========================================================================
    // Certificate queries
    // =========================================================================

    @GetMapping("/certificates/{serial}")
    @Operation(summary = "Get certificate details by serial number")
    public CertificateDto getCertificate(@PathVariable String serial) {
        return lifecycleService.getCertificateBySerial(serial);
    }

    @GetMapping("/certificates/{serial}/download")
    @Operation(summary = "Download certificate in PEM format")
    public ResponseEntity<String> downloadCertificate(
            @PathVariable String serial,
            @RequestParam(defaultValue = "pem") String format) {

        String content = lifecycleService.downloadCertificate(serial, format);
        String filename = serial + "." + format;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/x-pem-file"))
                .body(content);
    }
}
