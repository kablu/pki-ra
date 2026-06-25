package com.pki.ra.raservice.csr;

import com.pki.ra.common.certificate.CsrRequestTransitionRepository;
import com.pki.ra.common.model.CsrRequest;
import com.pki.ra.common.model.CsrRequestTransition;
import com.pki.ra.common.model.User;
import com.pki.ra.common.model.enums.CsrStatus;
import com.pki.ra.common.model.enums.CsrStatusTransition;
import com.pki.ra.common.exception.ExceptionFactory;
import com.pki.ra.raservice.error.RaErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Centralized service for all CSR status transitions.
 * Every status change MUST go through this service — it enforces
 * the state machine and records the transition history.
 */
@Slf4j
@Service
public class CsrTransitionService {

    private final CsrRequestTransitionRepository transitionRepo;
    private final ExceptionFactory exceptionFactory;

    public CsrTransitionService(CsrRequestTransitionRepository transitionRepo,
                                 ExceptionFactory exceptionFactory) {
        this.transitionRepo = transitionRepo;
        this.exceptionFactory = exceptionFactory;
    }

    @Transactional
    public void transition(CsrRequest request, CsrStatus newStatus,
                           User changedBy, String role, String remarks) {

        CsrStatus currentStatus = request.getStatus();

        if (!CsrStatusTransition.canTransition(currentStatus, newStatus)) {
            throw exceptionFactory.create(RaErrorCode.VALIDATION_INVALID_CSR,
                    "Invalid status transition: " + currentStatus + " → " + newStatus +
                    ". Allowed: " + CsrStatusTransition.validTargets(currentStatus));
        }

        CsrRequestTransition transition = CsrRequestTransition.builder()
                .request(request)
                .fromStatus(currentStatus)
                .toStatus(newStatus)
                .changedBy(changedBy)
                .changedByRole(role)
                .remarks(remarks)
                .build();

        transitionRepo.save(transition);

        request.setStatus(newStatus);

        log.info("CSR {} transitioned: {} → {} by {} ({})",
                request.getRequestId(), currentStatus, newStatus,
                changedBy.getUsername(), role);
    }
}
