package com.pki.ra.common.certificate;

import com.pki.ra.common.model.CertificateRequestApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificateRequestApprovalRepository extends JpaRepository<CertificateRequestApproval, Long> {

    List<CertificateRequestApproval> findByRequestId(Long requestId);

    boolean existsByRequestIdAndApproverId(Long requestId, Long approverId);
}
