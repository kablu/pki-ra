package com.pki.ra.raservice.csr;

import com.pki.ra.common.certificate.dto.csr.WorkflowConfigDto;
import com.pki.ra.common.config.ConfigBean;
import com.pki.ra.common.model.enums.ApprovalMode;
import com.pki.ra.common.model.enums.AssignmentMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reads and manages the configurable approval workflow settings.
 * All workflow decisions check this service for the current mode.
 */
@Slf4j
@Service
public class WorkflowConfigService {

    private final ConfigBean configBean;

    public WorkflowConfigService(ConfigBean configBean) {
        this.configBean = configBean;
    }

    public ApprovalMode getApprovalMode() {
        String value = configBean.getValue("approval_mode").orElse(null);
        return value != null ? ApprovalMode.valueOf(value) : ApprovalMode.DUAL;
    }

    public AssignmentMode getAssignmentMode() {
        String value = configBean.getValue("assignment_mode").orElse(null);
        return value != null ? AssignmentMode.valueOf(value) : AssignmentMode.HYBRID;
    }

    public int getMaxPendingPerOperator() {
        String value = configBean.getValue("max_pending_per_operator").orElse(null);
        return value != null ? Integer.parseInt(value) : 10;
    }

    public boolean isMakerCanReturnToAdmin() {
        String value = configBean.getValue("maker_can_return_to_admin").orElse(null);
        return value == null || Boolean.parseBoolean(value);
    }

    public boolean isRequireRemarks() {
        String value = configBean.getValue("require_remarks").orElse(null);
        return value == null || Boolean.parseBoolean(value);
    }

    public boolean isSingleMode() {
        return getApprovalMode() == ApprovalMode.SINGLE;
    }

    public boolean isDualMode() {
        return getApprovalMode() == ApprovalMode.DUAL;
    }

    public WorkflowConfigDto getCurrentConfig() {
        return WorkflowConfigDto.builder()
                .approvalMode(getApprovalMode().name())
                .assignmentMode(getAssignmentMode().name())
                .maxPendingPerOperator(getMaxPendingPerOperator())
                .makerCanReturnToAdmin(isMakerCanReturnToAdmin())
                .requireRemarks(isRequireRemarks())
                .build();
    }
}
