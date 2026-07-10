package com.pki.ra.common.certificate;

import com.pki.ra.common.model.Certificate;
import com.pki.ra.common.model.Certificate.CertificateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findBySerialNumber(String serialNumber);

    Optional<Certificate> findByFingerprintSha256(String fingerprint);

    Page<Certificate> findByStatus(CertificateStatus status, Pageable pageable);

    List<Certificate> findByStatusAndNotAfterBefore(CertificateStatus status, Instant before);

    @Query("SELECT c.status, COUNT(c) FROM Certificate c GROUP BY c.status")
    List<Object[]> countByStatusGrouped();

    long countByStatus(CertificateStatus status);
}
