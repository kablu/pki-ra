package com.pki.ra.raservice.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;

/**
 * PHASE 2 — Fires on {@link ApplicationEnvironmentPreparedEvent}.
 *
 * <p>By the time this event fires:
 * <ul>
 *   <li>Logging system is INITIALIZED — logback.xml has been loaded via {@code logging.config}</li>
 *   <li>{@code application-ide.properties} has been parsed into the {@link ConfigurableEnvironment}</li>
 *   <li>Active profiles are known</li>
 *   <li>ApplicationContext has NOT been created yet — no beans available</li>
 * </ul>
 *
 * <p>SLF4J is available here — logback FILE + AUDIT appenders are now active.
 *
 * <p>Must be registered in:
 * {@code META-INF/spring/org.springframework.context.ApplicationListener.imports}
 *
 * @author pki-ra
 * @since  1.0.0
 */
public class RaServiceEnvironmentListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(RaServiceEnvironmentListener.class);
    private static final String SEP = "-".repeat(65);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();

        log.info(SEP);
        log.info("  PKI-RA :: raservice  >>  PHASE 2 : Environment Prepared");
        log.info(SEP);
        row("Application Name",  env.getProperty("spring.application.name",           "pki-ra-raservice"));
        row("Active Profiles",   Arrays.toString(env.getActiveProfiles()));
        row("Server Port",       env.getProperty("server.port",                        "8083"));
        row("Context Path",      env.getProperty("server.servlet.context-path",        "/ra-api"));
        row("Logging Config",    env.getProperty("logging.config",                     "classpath default"));
        row("Config Import",     env.getProperty("spring.config.import",               "none"));
        row("JPA Enabled",       env.getProperty("spring.data.jpa.repositories.enabled", "false"));
        row("Autoconfigure Excl",env.getProperty("spring.autoconfigure.exclude",       "none"));
        row("Logging Status",    "logback.xml LOADED — FILE + AUDIT appenders active");
        log.info(SEP);
    }

    // -------------------------------------------------------------------------

    private static void row(String label, String value) {
        log.info("  {}", String.format("%-22s : %s", label, value));
    }
}
