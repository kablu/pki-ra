package com.pki.ra.raservice.audit;

import com.pki.ra.common.config.H2ConnectivityChecker;
import com.pki.ra.common.model.RequestLog;
import com.pki.ra.raservice.repository.RequestLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Saves seed {@link RequestLog} rows after the Spring context is fully loaded.
 *
 * <p>Triggered by {@link ApplicationReadyEvent} — guaranteed to fire after all
 * beans (including {@link H2ConnectivityChecker} and the JPA infrastructure)
 * are initialised and the H2 DataSource is confirmed reachable.
 *
 * <p>Active only under the {@code h2} profile.
 */
@Slf4j
@Component
@Profile("h2")
public class AuditLog {

    private final H2ConnectivityChecker db;
    private final RequestLogRepository  requestLogRepository;

    public AuditLog(@Qualifier("DB") H2ConnectivityChecker db,
                    RequestLogRepository requestLogRepository) {
        this.db                  = db;
        this.requestLogRepository = requestLogRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[AuditLog] ApplicationReadyEvent received — DB bean={}, saving RequestLog seed data", db);

        List<RequestLog> seed = List.of(
                RequestLog.builder().req("CERT_REQUEST_INIT")   .reqTs(LocalDateTime.now()).build(),
                RequestLog.builder().req("CERT_VALIDATE")       .reqTs(LocalDateTime.now()).build(),
                RequestLog.builder().req("CERT_APPROVE")        .reqTs(LocalDateTime.now()).build(),
                RequestLog.builder().req("CERT_REVOKE")         .reqTs(LocalDateTime.now()).build(),
                RequestLog.builder().req("RA_HEALTH_CHECK")     .reqTs(LocalDateTime.now()).build()
        );

        List<RequestLog> saved = requestLogRepository.saveAll(seed);

        log.info("[AuditLog] {} rows inserted into request_log table", saved.size());
        saved.forEach(r -> log.info("[AuditLog]  id={} | req={} | req_ts={}", r.getId(), r.getReq(), r.getReqTs()));
    }
}
