package com.pki.ra.raservice.checker;

import com.pki.ra.common.config.H2ConnectivityChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Verifies that the {@code "DB"} bean ({@link H2ConnectivityChecker}) defined in
 * {@code :common} has been successfully injected into {@code raservice}.
 *
 * <p>Active only under the {@code h2} profile — mirrors the profile guard on
 * {@link H2ConnectivityChecker} and {@code DataSourceConfig} in {@code :common}.
 *
 * <p>Spring calls {@link #afterPropertiesSet()} once all dependencies are wired,
 * which is guaranteed to be <em>after</em> {@link H2ConnectivityChecker#afterPropertiesSet()}
 * has already run its connectivity check (because this bean declares it as a dependency).
 * This class therefore logs a raservice-level confirmation that H2 is available.
 */
@Slf4j
@Component
@Profile("h2")
public class RaSVCH2Checker implements InitializingBean {

    private static final String BANNER =
            "\n╔══════════════════════════════════════════════════╗" +
            "\n║      RA-SVC  ·  H2 Bean Injection — OK           ║" +
            "\n╚══════════════════════════════════════════════════╝";

    private final H2ConnectivityChecker dbBean;

    /**
     * Injects the {@code "DB"} bean by qualifier — the H2 connectivity checker
     * declared in {@code :common} under profile {@code h2}.
     */
    public RaSVCH2Checker(@Qualifier("DB") H2ConnectivityChecker dbBean) {
        this.dbBean = dbBean;
    }

    @Override
    public void afterPropertiesSet() {
        log.info(BANNER);
        log.info("[RaSVCH2Checker] 'DB' bean injected — type={}, bean={}",
                dbBean.getClass().getSimpleName(), dbBean);
        log.info("[RaSVCH2Checker] H2 connectivity pre-check confirmed by :common — raservice context is DB-ready.");
    }
}
