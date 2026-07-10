package com.pki.ra.common.certificate.dto.csr;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalMatrixDto {

    private Long id;
    private String csrProfile;
    private String approvalMode;
    private String makerRole;
    private String checkerRole;
    private boolean adminCanBeMaker;
    private boolean adminCanBeChecker;
    private boolean checkerCanReturn;
    private int minRemarksLength;
    private boolean autoApprove;
    private boolean urgentAutoEscalate;
    private int maxPendingPerOperator;
    private boolean isActive;
}
