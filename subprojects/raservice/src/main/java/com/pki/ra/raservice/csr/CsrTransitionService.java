package com.pki.ra.raservice.csr;

import com.pki.ra.common.certificate.CsrRequestTransitionRepository;
import com.pki.ra.common.event.CsrStatusChangedEvent;
import com.pki.ra.common.exception.ExceptionFactory;
import com.pki.ra.common.model.CsrRequest;
import com.pki.ra.common.model.CsrRequestTransition;
import com.pki.ra.common.model.User;
import com.pki.ra.common.model.enums.CsrStatus;
import com.pki.ra.common.model.enums.CsrStatusTransition;
import com.pki.ra.raservice.error.RaErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Centralized service for ALL CSR status transitions.
 *
 * <p>Every status change MUST go through this service. It:
 * <ol>
 *   <li>Validates the transition via {@link CsrStatusTransition} state machine</li>
 *   <li>Records immutable history in {@code csr_request_transitions} table</li>
 *   <li>Updates the request status</li>
 *   <li>Publishes {@link CsrStatusChangedEvent} — listeners handle audit,
 *       notifications, SIEM forwarding (async, after commit)</li>
 * </ol>
 *
 * <p>Business services call {@code transition()} and do NOT call
 * audit/notification services directly — the event handles everything.
 */
@Slf4j
@Service
public class CsrTransitionService {

    private final CsrRequestTransitionRepository transitionRepo;
    private final ExceptionFactory exceptionFactory;
    private final ApplicationEventPublisher eventPublisher;

    public CsrTransitionService(CsrRequestTransitionRepository transitionRepo,
                                 ExceptionFactory exceptionFactory,
                                 ApplicationEventPublisher eventPublisher) {
        this.transitionRepo = transitionRepo;
        this.exceptionFactory = exceptionFactory;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Transition a CSR request to a new status.
     *
     * @param request   the CSR request to transition
     * @param newStatus the target status
     * @param changedBy the user performing the action
     * @param role      the actor's role (from approval_matrix, not hardcoded)
     * @param remarks   optional remarks/reason
     * @param ipAddress client IP for audit (nullable for system actions)
     */
    @Transactional
    public void transition(CsrRequest request, CsrStatus newStatus,
                           User changedBy, String role, String remarks,
                           String ipAddress) {

        CsrStatus currentStatus = request.getStatus();

        if (!CsrStatusTransition.canTransition(currentStatus, newStatus)) {
            throw exceptionFactory.create(RaErrorCode.VALIDATION_INVALID_CSR,
                    "Invalid status transition: " + currentStatus + " → " + newStatus +
                    ". Allowed: " + CsrStatusTransition.validTargets(currentStatus));
        }

        // Record immutable transition history
        CsrRequestTransition transition = CsrRequestTransition.builder()
                .request(request)
                .fromStatus(currentStatus)
                .toStatus(newStatus)
                .changedBy(changedBy)
                .changedByRole(role)
                .remarks(remarks)
                .build();

        transitionRepo.save(transition);

        // Update request status
        request.setStatus(newStatus);

        log.info("CSR {} transitioned: {} → {} by {} ({})",
                request.getRequestId(), currentStatus, newStatus,
                changedBy.getUsername(), role);

        // Publish event — listeners handle audit, notifications, etc.
        // Fires AFTER_COMMIT via @TransactionalEventListener
        eventPublisher.publishEvent(new CsrStatusChangedEvent(
                request.getRequestId(),
                request.getId(),
                currentStatus,
                newStatus,
                changedBy.getUsername(),
                changedBy.getId(),
                role,
                remarks,
                ipAddress,
                request.getCsrProfile() != null ? request.getCsrProfile().name() : null,
                Instant.now()
        ));
    }
}
