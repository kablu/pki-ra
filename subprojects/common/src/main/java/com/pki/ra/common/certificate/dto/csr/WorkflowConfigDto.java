package com.pki.ra.common.certificate.dto.csr;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkflowConfigDto {

    private String approvalMode;
    private String assignmentMode;
    private String autoAssignmentStrategy;
    private int maxPendingPerOperator;
    private boolean makerCanReturnToAdmin;
    private boolean requireRemarks;
}
