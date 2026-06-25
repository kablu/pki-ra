package com.pki.ra.common.certificate.dto.csr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReturnRequest {

    @NotBlank(message = "Return reason is required")
    @Size(max = 2000)
    private String reason;
}
