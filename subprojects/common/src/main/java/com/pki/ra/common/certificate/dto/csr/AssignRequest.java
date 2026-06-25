package com.pki.ra.common.certificate.dto.csr;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignRequest {

    @NotNull(message = "Operator ID is required")
    private Long operatorId;
}
