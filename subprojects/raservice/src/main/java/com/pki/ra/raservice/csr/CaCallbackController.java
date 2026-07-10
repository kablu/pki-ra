package com.pki.ra.raservice.csr;

import com.pki.ra.common.certificate.dto.csr.CaCallbackRequest;
import com.pki.ra.common.certificate.dto.csr.CsrRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Callback endpoint for external CA (WLCA) to post signed certificates
 * back to RA after asynchronous processing.
 *
 * <p>Flow: RA sends CSR → WLCA processes → WLCA POSTs certificate here.
 *
 * <p>This endpoint is NOT behind standard user authentication — it uses
 * a separate security mechanism (API key, mTLS, or IP allowlist)
 * appropriate for machine-to-machine communication.
 */
@Slf4j
@RestController
@RequestMapping("/api/ra/callback")
@Tag(name = "CA Callback", description = "Endpoint for external CA to post certificates")
public class CaCallbackController {

    private final CaIntegrationService caIntegrationService;

    public CaCallbackController(CaIntegrationService caIntegrationService) {
        this.caIntegrationService = caIntegrationService;
    }

    @PostMapping("/certificate")
    @Operation(summary = "Receive signed certificate from external CA (WLCA)")
    public ResponseEntity<CsrRequestDto> receiveCertificate(
            @Valid @RequestBody CaCallbackRequest callback) {

        log.info("CA callback received for requestId={}, status={}",
                callback.getRequestId(), callback.getStatus());

        CsrRequestDto result = caIntegrationService.handleCaCallback(callback);

        return ResponseEntity.ok(result);
    }
}
