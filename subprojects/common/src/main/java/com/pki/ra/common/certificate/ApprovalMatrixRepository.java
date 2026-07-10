package com.pki.ra.common.certificate;

import com.pki.ra.common.model.ApprovalMatrix;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalMatrixRepository extends JpaRepository<ApprovalMatrix, Long> {

    Optional<ApprovalMatrix> findByCsrProfileAndIsActiveTrue(String csrProfile);

    Optional<ApprovalMatrix> findByCsrProfileIsNullAndIsActiveTrue();

    List<ApprovalMatrix> findAllByIsActiveTrue();
}
