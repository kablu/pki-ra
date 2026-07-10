package com.pki.ra.common.certificate.dto.csr;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardSummaryDto {

    // Incoming
    private long received;
    private long validationFailed;

    // Pool
    private long submitted;

    // In Progress
    private long inReview;
    private long reviewed;
    private long approved;
    private long sentToCa;

    // Completed
    private long issued;
    private long closed;

    // Issues
    private long rejected;
    private long returned;
    private long failed;

    private long total;
}
