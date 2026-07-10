package com.pki.ra.raservice.csr;

import com.pki.ra.common.certificate.ApprovalMatrixRepository;
import com.pki.ra.common.certificate.dto.csr.ApprovalMatrixDto;
import com.pki.ra.common.model.ApprovalMatrix;
import com.pki.ra.common.model.enums.ApprovalMode;
import com.pki.ra.common.model.enums.CsrProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves approval rules from the configurable approval_matrix table.
 * NOTHING is hardcoded — every rule comes from the database.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Profile-specific rule (csr_profile = 'TLS_SERVER')</li>
 *   <li>Global default (csr_profile = NULL)</li>
 * </ol>
 */
@Slf4j
@Service
public class ApprovalMatrixService {

    private final ApprovalMatrixRepository matrixRepo;

    public ApprovalMatrixService(ApprovalMatrixRepository matrixRepo) {
        this.matrixRepo = matrixRepo;
    }

    /**
     * Resolve the approval matrix rule for a given CSR profile.
     * Falls back to global default (csr_profile = NULL) if no profile-specific rule.
     */
    public ApprovalMatrix resolveForProfile(CsrProfile profile) {
        return matrixRepo.findByCsrProfileAndIsActiveTrue(profile.name())
                .orElseGet(() -> matrixRepo.findByCsrProfileIsNullAndIsActiveTrue()
                        .orElseThrow(() -> new IllegalStateException(
                                "No approval matrix found — neither for profile " +
                                profile + " nor global default. Admin must configure.")));
    }

    public ApprovalMode getApprovalMode(CsrProfile profile) {
        return ApprovalMode.valueOf(resolveForProfile(profile).getApprovalMode());
    }

    public boolean isSingleMode(CsrProfile profile) {
        return getApprovalMode(profile) == ApprovalMode.SINGLE;
    }

    public boolean isDualMode(CsrProfile profile) {
        return getApprovalMode(profile) == ApprovalMode.DUAL;
    }

    public String getMakerRole(CsrProfile profile) {
        return resolveForProfile(profile).getMakerRole();
    }

    public String getCheckerRole(CsrProfile profile) {
        return resolveForProfile(profile).getCheckerRole();
    }

    public boolean canAdminBeMaker(CsrProfile profile) {
        return resolveForProfile(profile).isAdminCanBeMaker();
    }

    public boolean canAdminBeChecker(CsrProfile profile) {
        return resolveForProfile(profile).isAdminCanBeChecker();
    }

    public boolean canCheckerReturn(CsrProfile profile) {
        return resolveForProfile(profile).isCheckerCanReturn();
    }

    public int getMinRemarksLength(CsrProfile profile) {
        return resolveForProfile(profile).getMinRemarksLength();
    }

    public boolean isAutoApprove(CsrProfile profile) {
        return resolveForProfile(profile).isAutoApprove();
    }

    public int getMaxPendingPerOperator(CsrProfile profile) {
        return resolveForProfile(profile).getMaxPendingPerOperator();
    }

    // =========================================================================
    // CRUD for Admin
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ApprovalMatrixDto> getAllRules() {
        return matrixRepo.findAllByIsActiveTrue().stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public ApprovalMatrixDto updateRule(Long id, ApprovalMatrixDto dto) {
        ApprovalMatrix rule = matrixRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Matrix rule not found: " + id));

        rule.setApprovalMode(dto.getApprovalMode());
        rule.setMakerRole(dto.getMakerRole());
        rule.setCheckerRole(dto.getCheckerRole());
        rule.setAdminCanBeMaker(dto.isAdminCanBeMaker());
        rule.setAdminCanBeChecker(dto.isAdminCanBeChecker());
        rule.setCheckerCanReturn(dto.isCheckerCanReturn());
        rule.setMinRemarksLength(dto.getMinRemarksLength());
        rule.setAutoApprove(dto.isAutoApprove());
        rule.setUrgentAutoEscalate(dto.isUrgentAutoEscalate());
        rule.setMaxPendingPerOperator(dto.getMaxPendingPerOperator());

        matrixRepo.save(rule);
        log.info("Approval matrix updated: profile={}, mode={}", rule.getCsrProfile(), rule.getApprovalMode());
        return toDto(rule);
    }

    @Transactional
    public ApprovalMatrixDto createRule(ApprovalMatrixDto dto) {
        ApprovalMatrix rule = ApprovalMatrix.builder()
                .csrProfile(dto.getCsrProfile())
                .approvalMode(dto.getApprovalMode())
                .makerRole(dto.getMakerRole())
                .checkerRole(dto.getCheckerRole())
                .adminCanBeMaker(dto.isAdminCanBeMaker())
                .adminCanBeChecker(dto.isAdminCanBeChecker())
                .checkerCanReturn(dto.isCheckerCanReturn())
                .minRemarksLength(dto.getMinRemarksLength())
                .autoApprove(dto.isAutoApprove())
                .urgentAutoEscalate(dto.isUrgentAutoEscalate())
                .maxPendingPerOperator(dto.getMaxPendingPerOperator())
                .build();

        matrixRepo.save(rule);
        log.info("Approval matrix rule created: profile={}, mode={}", rule.getCsrProfile(), rule.getApprovalMode());
        return toDto(rule);
    }

    private ApprovalMatrixDto toDto(ApprovalMatrix m) {
        return ApprovalMatrixDto.builder()
                .id(m.getId())
                .csrProfile(m.getCsrProfile())
                .approvalMode(m.getApprovalMode())
                .makerRole(m.getMakerRole())
                .checkerRole(m.getCheckerRole())
                .adminCanBeMaker(m.isAdminCanBeMaker())
                .adminCanBeChecker(m.isAdminCanBeChecker())
                .checkerCanReturn(m.isCheckerCanReturn())
                .minRemarksLength(m.getMinRemarksLength())
                .autoApprove(m.isAutoApprove())
                .urgentAutoEscalate(m.isUrgentAutoEscalate())
                .maxPendingPerOperator(m.getMaxPendingPerOperator())
                .isActive(m.isActive())
                .build();
    }
}
