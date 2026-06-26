package com.pki.ra.raservice.csr;

import com.pki.ra.common.certificate.WorkflowConfigRepository;
import com.pki.ra.common.certificate.dto.csr.WorkflowConfigDto;
import com.pki.ra.common.model.WorkflowConfig;
import com.pki.ra.common.model.enums.ApprovalMode;
import com.pki.ra.common.model.enums.AssignmentMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reads and manages the configurable approval workflow settings
 * from the dedicated {@code workflow_config} table.
 *
 * <p>All workflow decisions check this service for the current mode.
 * Separate from {@code ConfigBean} which handles LDAP, mail, etc.
 */
@Slf4j
@Service
public class WorkflowConfigService {

    private final WorkflowConfigRepository configRepo;

    public WorkflowConfigService(WorkflowConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    public ApprovalMode getApprovalMode() {
        String value = getValue("approval_mode");
        return value != null ? ApprovalMode.valueOf(value) : ApprovalMode.DUAL;
    }

    public AssignmentMode getAssignmentMode() {
        String value = getValue("assignment_mode");
        return value != null ? AssignmentMode.valueOf(value) : AssignmentMode.HYBRID;
    }

    public int getMaxPendingPerOperator() {
        String value = getValue("max_pending_per_operator");
        return value != null ? Integer.parseInt(value) : 10;
    }

    public boolean isMakerCanReturnToAdmin() {
        String value = getValue("maker_can_return_to_admin");
        return value == null || Boolean.parseBoolean(value);
    }

    public boolean isRequireRemarks() {
        String value = getValue("require_remarks");
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
                .autoAssignmentStrategy(getValue("auto_assignment_strategy"))
                .maxPendingPerOperator(getMaxPendingPerOperator())
                .makerCanReturnToAdmin(isMakerCanReturnToAdmin())
                .requireRemarks(isRequireRemarks())
                .build();
    }

    public void updateConfig(WorkflowConfigDto dto) {
        updateValue("approval_mode", dto.getApprovalMode());
        updateValue("assignment_mode", dto.getAssignmentMode());
        if (dto.getAutoAssignmentStrategy() != null) {
            updateValue("auto_assignment_strategy", dto.getAutoAssignmentStrategy());
        }
        updateValue("max_pending_per_operator", String.valueOf(dto.getMaxPendingPerOperator()));
        updateValue("maker_can_return_to_admin", String.valueOf(dto.isMakerCanReturnToAdmin()));
        updateValue("require_remarks", String.valueOf(dto.isRequireRemarks()));

        log.info("Workflow config updated: mode={}, assignment={}",
                dto.getApprovalMode(), dto.getAssignmentMode());
    }

    private String getValue(String key) {
        return configRepo.findByConfigKeyAndIsActiveTrue(key)
                .map(WorkflowConfig::getConfigValue)
                .orElse(null);
    }

    private void updateValue(String key, String value) {
        configRepo.findByConfigKeyAndIsActiveTrue(key).ifPresent(config -> {
            config.setConfigValue(value);
            configRepo.save(config);
        });
    }
}
