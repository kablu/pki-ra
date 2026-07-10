package com.pki.ra.common.certificate.dto.csr;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RemarksRequest {

    @Size(max = 2000)
    private String remarks;
}
