package com.pki.ra.common.certificate;

import com.pki.ra.common.model.CertificateRequest;
import com.pki.ra.common.model.CertificateRequest.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificateRequestRepository extends JpaRepository<CertificateRequest, Long> {

    Page<CertificateRequest> findByStatus(RequestStatus status, Pageable pageable);

    List<CertificateRequest> findByRequesterId(Long requesterId);

    Page<CertificateRequest> findByRequesterIdAndStatus(Long requesterId, RequestStatus status, Pageable pageable);

    long countByStatus(RequestStatus status);
}
