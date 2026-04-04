package com.pki.ra.raservice.listener;

import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * PHASE 1 — Fires on {@link ApplicationStartingEvent}.
 *
 * <p>This is the very first lifecycle event in Spring Boot — fires BEFORE:
 * <ul>
 *   <li>Logging system initialization (logback.xml not yet loaded)</li>
 *   <li>Environment / application.properties loading</li>
 *   <li>ApplicationContext creation</li>
 * </ul>
 *
 * <p><b>SLF4J is NOT available here</b> — {@code System.out} is used intentionally.
 *
 * <p>Must be registered in:
 * {@code META-INF/spring/org.springframework.context.ApplicationListener.imports}
 *
 * @author pki-ra
 * @since  1.0.0
 */
public class RaServiceStartingListener implements ApplicationListener<ApplicationStartingEvent> {

    private static final String SEP  = "=".repeat(65);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void onApplicationEvent(ApplicationStartingEvent event) {
        System.out.println(SEP);
        System.out.println("  PKI-RA :: raservice  >>  PHASE 1 : Application Starting");
        System.out.println(SEP);
        row("Timestamp",      LocalDateTime.now().format(FMT));
        row("Java Version",   System.getProperty("java.version"));
        row("JVM Vendor",     System.getProperty("java.vm.vendor"));
        row("JVM Name",       System.getProperty("java.vm.name"));
        row("OS",             System.getProperty("os.name") + " " + System.getProperty("os.version"));
        row("Architecture",   System.getProperty("os.arch"));
        row("PID",            String.valueOf(ManagementFactory.getRuntimeMXBean().getPid()));
        row("Logging Status", "Bootstrap — logback.xml NOT YET loaded (System.out only)");
        System.out.println(SEP);
    }

    // -------------------------------------------------------------------------

    private static void row(String label, String value) {
        System.out.printf("  %-20s : %s%n", label, value);
    }
}
