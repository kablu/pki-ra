package com.pki.ra.common.certificate;

import com.pki.ra.common.model.CsrRequestTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CsrRequestTransitionRepository extends JpaRepository<CsrRequestTransition, Long> {

    List<CsrRequestTransition> findByRequestIdOrderByCreatedAtAsc(Long requestId);
}
