package com.pki.ra.raservice.certificate;

import com.pki.ra.common.certificate.CaClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * REST-based CA client that calls the caservice module's /api/ca/sign endpoint.
 */
@Slf4j
@Component
public class BuiltinCaClient implements CaClient {

    private final RestClient restClient;

    public BuiltinCaClient(@Value("${ra.ca.base-url:http://localhost:8082}") String caBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(caBaseUrl)
                .build();
    }

    @Override
    public CaSigningResult sign(String csrPem, Integer validityDays, String sans) {
        Map<String, Object> body = new HashMap<>();
        body.put("pkcs10", csrPem);
        if (validityDays != null) body.put("validityDays", validityDays);
        if (sans != null) body.put("subjectAltNames", sans);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/api/ca/sign")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new RuntimeException("CA returned null response");
        }

        return new CaSigningResult(
                (String) response.get("certificate"),
                (String) response.get("certificateChain"),
                (String) response.get("serialNumber"),
                (String) response.get("signatureAlgorithm"),
                (String) response.get("subject"),
                (String) response.get("issuer"),
                (String) response.get("notBefore"),
                (String) response.get("notAfter")
        );
    }
}
