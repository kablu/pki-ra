package com.pki.ra.raservice.csr;

import com.pki.ra.common.certificate.CsrRequestRepository;
import com.pki.ra.common.certificate.dto.csr.CaCallbackRequest;
import com.pki.ra.common.certificate.dto.csr.CaSubmissionRequest;
import com.pki.ra.common.certificate.dto.csr.CsrRequestDto;
import com.pki.ra.common.exception.ExceptionFactory;
import com.pki.ra.common.model.CsrRequest;
import com.pki.ra.common.model.User;
import com.pki.ra.common.model.enums.CsrStatus;
import com.pki.ra.common.user.UserRepository;
import com.pki.ra.raservice.error.RaErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * Handles async communication between RA and external CA (WLCA).
 *
 * <p>Flow:
 * <ol>
 *   <li>APPROVED → {@link #sendToCa} → posts CSR to WLCA → status = SENT_TO_CA</li>
 *   <li>WLCA processes → posts certificate to RA's callback URL</li>
 *   <li>{@link #handleCaCallback} → receives certificate → status = ISSUED (or FAILED)</li>
 * </ol>
 *
 * <p>Backward compatible: if {@code ra.ca.base-url} is not configured,
 * the service logs a warning but does not fail — the sync flow
 * via {@code CaClient} in the old controller still works.
 */
@Slf4j
@Service
public class CaIntegrationService {

    private final CsrRequestRepository csrRepo;
    private final UserRepository userRepo;
    private final CsrTransitionService transitionService;
    private final ExceptionFactory exceptionFactory;
    private final RestClient restClient;
    private final String raCallbackBaseUrl;

    public CaIntegrationService(CsrRequestRepository csrRepo,
                                 UserRepository userRepo,
                                 CsrTransitionService transitionService,
                                 ExceptionFactory exceptionFactory,
                                 @Value("${ra.ca.base-url:}") String caBaseUrl,
                                 @Value("${ra.callback.base-url:http://localhost:8083/ra-api}") String raCallbackBaseUrl) {
        this.csrRepo = csrRepo;
        this.userRepo = userRepo;
        this.transitionService = transitionService;
        this.exceptionFactory = exceptionFactory;
        this.raCallbackBaseUrl = raCallbackBaseUrl;
        this.restClient = caBaseUrl.isBlank()
                ? null
                : RestClient.builder().baseUrl(caBaseUrl).build();
    }

    // =========================================================================
    // SEND TO CA (RA → WLCA)
    // =========================================================================

    @Transactional
    public CsrRequestDto sendToCa(Long requestId, String systemUsername) {
        CsrRequest request = csrRepo.findById(requestId)
                .orElseThrow(() -> exceptionFactory.create(RaErrorCode.CERT_NOT_FOUND, String.valueOf(requestId)));

        if (request.getStatus() != CsrStatus.APPROVED) {
            throw exceptionFactory.create(RaErrorCode.VALIDATION_INVALID_CSR,
                    "Only APPROVED requests can be sent to CA. Current: " + request.getStatus());
        }

        User systemUser = userRepo.findByUsername(systemUsername)
                .orElseGet(() -> userRepo.findByUsername("system")
                        .orElseThrow(() -> new IllegalStateException("System user not found")));

        String postBackUrl = raCallbackBaseUrl + "/api/ra/callback/certificate";
        request.setPostBackUrl(postBackUrl);
        request.setSentToCaAt(Instant.now());

        // Build submission payload
        CaSubmissionRequest caRequest = CaSubmissionRequest.builder()
                .requestId(request.getRequestId())
                .csrPem(request.getCsrPem())
                .csrProfile(request.getCsrProfile().name())
                .validityDays(request.getRequestedValidityDays())
                .subjectAltNames(request.getSubjectAltNames())
                .postBackUrl(postBackUrl)
                .build();

        try {
            if (restClient == null) {
                log.warn("CA base URL not configured — skipping CA submission for {}",
                        request.getRequestId());
                throw new IllegalStateException("CA base URL not configured");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/api/ca/sign-async")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(caRequest)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("caTransactionId")) {
                request.setCaTransactionId((String) response.get("caTransactionId"));
            }

            transitionService.transition(request, CsrStatus.SENT_TO_CA,
                    systemUser, "SYSTEM", "CSR sent to external CA (WLCA)", null);

            csrRepo.save(request);

            log.info("CSR {} sent to CA. caTransactionId={}, postBackUrl={}",
                    request.getRequestId(), request.getCaTransactionId(), postBackUrl);

        } catch (Exception e) {
            request.setStatusReason("Failed to send to CA: " + e.getMessage());
            transitionService.transition(request, CsrStatus.FAILED,
                    systemUser, "SYSTEM", "CA submission failed: " + e.getMessage(), null);
            csrRepo.save(request);

            log.error("Failed to send CSR {} to CA: {}", request.getRequestId(), e.getMessage());
        }

        return toDto(request);
    }

    // =========================================================================
    // HANDLE CA CALLBACK (WLCA → RA)
    // =========================================================================

    @Transactional
    public CsrRequestDto handleCaCallback(CaCallbackRequest callback) {
        CsrRequest request = csrRepo.findByRequestId(callback.getRequestId())
                .orElseThrow(() -> exceptionFactory.create(RaErrorCode.CERT_NOT_FOUND,
                        callback.getRequestId()));

        if (request.getStatus() != CsrStatus.SENT_TO_CA) {
            throw exceptionFactory.create(RaErrorCode.VALIDATION_INVALID_CSR,
                    "Callback received but request is not in SENT_TO_CA status. Current: " +
                    request.getStatus());
        }

        User systemUser = userRepo.findByUsername("system")
                .orElseThrow(() -> new IllegalStateException("System user not found"));

        request.setCaResponseReceivedAt(Instant.now());
        if (callback.getCaTransactionId() != null) {
            request.setCaTransactionId(callback.getCaTransactionId());
        }

        if ("ISSUED".equalsIgnoreCase(callback.getStatus())) {

            transitionService.transition(request, CsrStatus.ISSUED,
                    systemUser, "SYSTEM",
                    "Certificate received from CA. Serial: " + callback.getSerialNumber(),
                    null);

            log.info("Certificate received for CSR {}. Serial={}",
                    request.getRequestId(), callback.getSerialNumber());

        } else {

            request.setStatusReason(callback.getFailureReason());
            transitionService.transition(request, CsrStatus.FAILED,
                    systemUser, "SYSTEM",
                    "CA returned failure: " + callback.getFailureReason(),
                    null);

            log.warn("CA returned failure for CSR {}: {}",
                    request.getRequestId(), callback.getFailureReason());
        }

        csrRepo.save(request);
        return toDto(request);
    }

    private CsrRequestDto toDto(CsrRequest r) {
        return CsrRequestDto.builder()
                .id(r.getId())
                .requestId(r.getRequestId())
                .clientTxnId(r.getClientTxnId())
                .status(r.getStatus().name())
                .statusReason(r.getStatusReason())
                .build();
    }
}
