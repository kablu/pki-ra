package com.pki.ra.raservice.listener;

import com.pki.ra.common.util.HelloTest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * PHASE 3 — Fires on {@link ApplicationReadyEvent}.
 *
 * <p>By the time this event fires:
 * <ul>
 *   <li>ApplicationContext is fully refreshed</li>
 *   <li>All Spring beans are instantiated, wired and ready</li>
 *   <li>Embedded server (Tomcat) is running and accepting requests</li>
 * </ul>
 *
 * <p>Prints a startup summary specific to {@code raservice}:
 * <ul>
 *   <li>All beans registered under {@code com.pki.ra.raservice}</li>
 *   <li>Total bean count in the ApplicationContext</li>
 *   <li>Swagger UI and Actuator URLs</li>
 *   <li>JVM uptime since process start</li>
 * </ul>
 *
 * <p>Registered as a {@link Component} — no META-INF registration needed
 * because ApplicationContext is already available at this phase.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaServiceReadyListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final String RA_BASE_PACKAGE = "com.pki.ra.raservice";
    private static final String SEP_THICK = "=".repeat(65);
    private static final String SEP_THIN  = "-".repeat(65);

    private final ApplicationContext context;
    private final Environment        environment;
    private final HelloTest          helloTest;

    // -------------------------------------------------------------------------

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String port        = environment.getProperty("server.port",                 "8083");
        String contextPath = environment.getProperty("server.servlet.context-path", "/ra-api");
        String appName     = environment.getProperty("spring.application.name",     "pki-ra-raservice");

        log.info(SEP_THICK);
        log.info("  PKI-RA :: raservice  >>  PHASE 3 : Application Ready");
        log.info(SEP_THICK);

        // -- Service URLs --------------------------------------------------------
        log.info("  SERVICE URLS");
        log.info(SEP_THIN);
        row("Base URL",        "http://localhost:" + port + contextPath);
        row("Swagger UI",      "http://localhost:" + port + contextPath + "/swagger-ui.html");
        row("OpenAPI Docs",    "http://localhost:" + port + contextPath + "/v3/api-docs");
        row("Health",          "http://localhost:" + port + contextPath + "/actuator/health");
        row("Metrics",         "http://localhost:" + port + contextPath + "/actuator/metrics");
        log.info(SEP_THIN);

        // -- Application Metadata ------------------------------------------------
        log.info("  APPLICATION METADATA");
        log.info(SEP_THIN);
        row("Application",     appName);
        row("Active Profiles", Arrays.toString(environment.getActiveProfiles()));
        row("JVM Uptime",      formatUptime(event.getSpringApplication()
                .getMainApplicationClass()));
        row("Total Beans",     String.valueOf(context.getBeanDefinitionCount()));
        log.info(SEP_THIN);

        // -- HelloTest bean from common module -----------------------------------
        log.info("  COMMON MODULE BEAN");
        log.info(SEP_THIN);
        row("HelloTest", helloTest.sayHello());
        log.info(SEP_THIN);

        // -- RA Beans loaded in raservice package --------------------------------
        List<String> raBeans = Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> isRaServiceBean(name))
                .sorted()
                .toList();

        log.info("  RASERVICE BEANS  ({} registered under {}.*)", raBeans.size(), RA_BASE_PACKAGE);
        log.info(SEP_THIN);
        raBeans.forEach(name -> log.info("  [bean]  {}", name));
        log.info(SEP_THICK);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the bean's actual class belongs to the raservice base package.
     */
    private boolean isRaServiceBean(String beanName) {
        try {
            Object bean = context.getBean(beanName);
            return bean.getClass().getPackageName().startsWith(RA_BASE_PACKAGE);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Returns a human-readable JVM uptime string (e.g. {@code 3s 412ms}).
     */
    private static String formatUptime(Class<?> ignored) {
        long uptimeMs = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds  = uptimeMs / 1000;
        long millis   = uptimeMs % 1000;
        return seconds + "s " + millis + "ms";
    }

    private static void row(String label, String value) {
        log.info("  {}", String.format("%-18s : %s", label, value));
    }
}
