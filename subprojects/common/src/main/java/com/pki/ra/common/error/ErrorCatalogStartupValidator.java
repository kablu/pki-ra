package com.pki.ra.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that every {@link ErrorCodeKey} registered in the application
 * has a corresponding row in the {@code error_catalog} DB table.
 *
 * <h3>When it runs</h3>
 * Fires on {@link ApplicationReadyEvent} at {@code @Order(20)} — after all
 * cache beans load at {@code @Order(10)}.  The catalog is guaranteed to be
 * populated before this validator executes.
 *
 * <h3>What it does</h3>
 * <ol>
 *   <li>Collects every {@link ErrorCodeKeySource} bean registered across all
 *       modules ({@code @Autowired List<ErrorCodeKeySource>}).</li>
 *   <li>Iterates all codes returned by each source.</li>
 *   <li>Checks each code against {@link ErrorCatalogProvider#containsInternalCode}.</li>
 *   <li>Logs a {@code WARN} for every missing code — so gaps are caught at
 *       deployment time, not silently at the first API call that triggers the
 *       fallback path in {@link com.pki.ra.common.exception.ExceptionFactory}.</li>
 * </ol>
 *
 * <h3>No fail-fast by default</h3>
 * Validation is non-blocking — a missing code logs a warning but does NOT
 * prevent startup. This is intentional: a partial catalog is better than a
 * service that refuses to start.  Enable fail-fast by overriding the check
 * or adding an {@code @ConditionalOnProperty} variant if your ops team prefers it.
 *
 * <h3>Registration — one @Bean per module</h3>
 * <pre>{@code
 * // In a @Configuration class inside the module:
 * @Bean
 * public ErrorCodeKeySource raErrorCodeKeySource() {
 *     return RaErrorCode::values;
 * }
 * }</pre>
 * No changes to this class needed when a new module is added.
 *
 * @see ErrorCodeKeySource
 * @see ErrorCatalogProvider
 * @see ErrorCodeKey
 * @author pki-ra
 * @since  1.0.0
 */
@Component
public class ErrorCatalogStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(ErrorCatalogStartupValidator.class);

    private final ErrorCatalogProvider catalog;

    /** Collects every ErrorCodeKeySource bean across all modules — empty list if none registered. */
    @Autowired(required = false)
    private List<ErrorCodeKeySource> sources = new ArrayList<>();

    public ErrorCatalogStartupValidator(ErrorCatalogProvider catalog) {
        this.catalog = catalog;
    }

    /**
     * Runs after all caches are loaded (Order 10) to validate catalog completeness.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(20)
    public void validate() {
        if (sources.isEmpty()) {
            log.debug("ErrorCatalogStartupValidator: no ErrorCodeKeySource beans registered — skipping validation.");
            return;
        }

        List<String> missing = new ArrayList<>();

        for (ErrorCodeKeySource source : sources) {
            for (ErrorCodeKey key : source.getAllCodes()) {
                if (!catalog.containsInternalCode(key.code())) {
                    missing.add(key.code());
                }
            }
        }

        if (missing.isEmpty()) {
            log.info("ErrorCatalogStartupValidator: all {} code source(s) validated — no missing entries.",
                     sources.size());
        } else {
            log.warn("ErrorCatalogStartupValidator: {} error code(s) defined in code but MISSING from error_catalog: {}",
                     missing.size(), missing);
            log.warn("ErrorCatalogStartupValidator: missing codes will use the ERR-999 fallback at runtime. " +
                     "Insert the missing rows and call POST /api/admin/error-catalog/refresh to fix without restarting.");
        }
    }
}
