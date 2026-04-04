package com.pki.ra.raservice;

import com.pki.ra.raservice.listener.RaServiceEnvironmentListener;
import com.pki.ra.raservice.listener.RaServiceStartingListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RaServiceApplication {

    /**
     * Spring Boot 4 — early lifecycle listeners are registered directly here
     * via {@link SpringApplication#addListeners}, instead of
     * {@code META-INF/spring/org.springframework.context.ApplicationListener.imports}.
     *
     * <p>This is the recommended approach in Spring Boot 4:
     * <ul>
     *   <li>Explicit — no hidden classpath scanning</li>
     *   <li>Type-safe — compiler catches missing/renamed classes</li>
     *   <li>No extra file to maintain</li>
     * </ul>
     *
     * <p>{@link RaServiceStartingListener} and {@link RaServiceEnvironmentListener}
     * fire BEFORE the {@code ApplicationContext} is created, so they cannot
     * be registered as {@code @Component} beans — they must be added here.
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(RaServiceApplication.class);

        // Phase 1 — ApplicationStartingEvent (before logging init)
        application.addListeners(new RaServiceStartingListener());

        // Phase 2 — ApplicationEnvironmentPreparedEvent (after logback.xml loaded)
        application.addListeners(new RaServiceEnvironmentListener());

        application.run(args);
    }
}
