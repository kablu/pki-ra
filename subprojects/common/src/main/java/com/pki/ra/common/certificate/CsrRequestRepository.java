package com.pki.ra.common.certificate;

import com.pki.ra.common.model.CsrRequest;
import com.pki.ra.common.model.enums.CsrStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CsrRequestRepository extends JpaRepository<CsrRequest, Long>,
        JpaSpecificationExecutor<CsrRequest> {

    Optional<CsrRequest> findByRequestId(String requestId);

    Optional<CsrRequest> findByClientTxnId(String clientTxnId);

    Optional<CsrRequest> findByCsrHash(String csrHash);

    boolean existsByClientTxnId(String clientTxnId);

    boolean existsByCsrHash(String csrHash);

    Page<CsrRequest> findByStatus(CsrStatus status, Pageable pageable);

    List<CsrRequest> findByAssignedToIdAndStatusIn(Long operatorId, List<CsrStatus> statuses);

    long countByStatus(CsrStatus status);

    long countByAssignedToIdAndStatusIn(Long operatorId, List<CsrStatus> statuses);

    @Query("SELECT r.status, COUNT(r) FROM CsrRequest r GROUP BY r.status")
    List<Object[]> countByStatusGrouped();
}
