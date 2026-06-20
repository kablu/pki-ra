package com.pki.ra.common.error;

import com.pki.ra.common.config.AbstractRefreshableCache;
import com.pki.ra.common.error.dto.ErrorCatalogDto;
import com.pki.ra.common.model.ErrorCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// ErrorCatalogProvider — decouples callers (ExceptionFactory, GlobalExceptionHandler)
// from this concrete class. Tests mock the interface; production autowires this bean.

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory cache of all active {@code error_catalog} rows.
 *
 * <p>Loaded once on {@link org.springframework.boot.context.event.ApplicationReadyEvent}
 * — after the full Spring context is ready. Two indexes are maintained:
 * <ul>
 *   <li>Primary  — {@link #cache}, keyed by {@code internal_code} (used inside the application)</li>
 *   <li>Secondary — {@link #byExternalCode}, keyed by {@code external_code} (API/UI error lookup)</li>
 * </ul>
 *
 * <p>No DB calls on the request path after startup.
 * Call {@code POST /api/admin/error-catalog/refresh} to hot-reload without restarting.
 *
 * <h3>Extending {@link AbstractRefreshableCache}</h3>
 * The following are fully inherited — zero duplication here:
 * <ul>
 *   <li>{@code applicationName} + {@link #getServiceName()}</li>
 *   <li>{@code loadOnReady()} — {@code @EventListener(ApplicationReadyEvent)} startup trigger</li>
 *   <li>{@code refresh(String)} — hot-reload body + {@link com.pki.ra.common.config.dto.RefreshResult}</li>
 *   <li>{@link #getAll()}, {@link #size()}, {@link #containsKey(String)}</li>
 * </ul>
 * This class only implements {@link #entityClass()}, {@link #doLoad()},
 * {@link #printTable()}, and the catalog-specific lookup API.
 *
 * <p>Usage:
 * <pre>{@code
 * ErrorCatalogDto err = errorCatalogBean.getByInternalCode("PKI_CERT_001").orElseThrow();
 * ErrorCatalogDto err = errorCatalogBean.getByExternalCode("ERR-001").orElseThrow();
 * List<ErrorCatalogDto> certErrors = errorCatalogBean.getByCategory("CERTIFICATE");
 * }</pre>
 *
 * @see AbstractRefreshableCache
 * @see com.pki.ra.common.exception.ExceptionFactory
 */
@Service
public class ErrorCatalogBean extends AbstractRefreshableCache<ErrorCatalog, ErrorCatalogDto>
        implements ErrorCatalogProvider {

    private final ErrorCatalogRepository repository;

    // Secondary index: external_code → dto  (primary = cache, keyed by internal_code)
    private final ConcurrentHashMap<String, ErrorCatalogDto> byExternalCode = new ConcurrentHashMap<>();

    public ErrorCatalogBean(ErrorCatalogRepository repository,
                            @Value("${spring.application.name}") String applicationName) {
        super(applicationName);
        this.repository = repository;
    }

    // =========================================================================
    // Refreshable — must implement
    // =========================================================================

    @Override
    public Class<?> entityClass() {
        return ErrorCatalog.class;
    }

    // =========================================================================
    // AbstractRefreshableCache — must implement
    // =========================================================================

    /**
     * Clears both indexes and repopulates from all active {@code error_catalog} rows.
     * Called on startup and on every hot-reload.
     */
    @Override
    protected void doLoad() {
        cache.clear();
        byExternalCode.clear();

        repository.findAllActive().forEach(row -> {
            ErrorCatalogDto dto = toDto(row);
            cache.put(row.getInternalCode(), dto);            // primary index
            byExternalCode.put(row.getExternalCode(), dto);   // secondary index
        });
    }

    @Override
    protected void printTable() {
        if (cache.isEmpty()) {
            log.warn("ErrorCatalogBean: no active error catalog entries found.");
            return;
        }

        int intW = Math.max(cache.values().stream().mapToInt(e -> e.internalCode().length()).max().orElse(15), 13);
        int extW = Math.max(cache.values().stream().mapToInt(e -> e.externalCode().length()).max().orElse(8),  13);
        int catW = Math.max(cache.values().stream().mapToInt(e -> e.category().length()).max().orElse(8),       8);
        int sevW = Math.max(cache.values().stream().mapToInt(e -> e.severity().length()).max().orElse(8),       8);
        int msgW = Math.min(cache.values().stream().mapToInt(e -> e.message().length()).max().orElse(20), 40);

        String fmt     = "| %-" + intW + "s | %-" + extW + "s | %-" + catW + "s | %-" + sevW + "s | %4s | %-" + msgW + "s |";
        String divider = "+" + "-".repeat(intW + 2) + "+" + "-".repeat(extW + 2)
                       + "+" + "-".repeat(catW + 2) + "+" + "-".repeat(sevW + 2)
                       + "+" + "-".repeat(6) + "+" + "-".repeat(msgW + 2) + "+";

        log.info("ErrorCatalogBean: {} active error entries loaded", cache.size());
        log.info(divider);
        log.info(String.format(fmt, "internal_code", "external_code", "category", "severity", "http", "message"));
        log.info(divider);
        cache.values().stream()
                .sorted((a, b) -> {
                    int cmp = a.category().compareTo(b.category());
                    return cmp != 0 ? cmp : a.internalCode().compareTo(b.internalCode());
                })
                .forEach(e -> {
                    String msg = e.message().length() > msgW
                            ? e.message().substring(0, msgW - 3) + "..."
                            : e.message();
                    log.info(String.format(fmt,
                            e.internalCode(), e.externalCode(),
                            e.category(), e.severity(), e.httpStatus(), msg));
                });
        log.info(divider);
    }

    // =========================================================================
    // Catalog lookup API — ErrorCatalogBean-specific
    // =========================================================================

    /** Lookup by internal code — use inside the service/exception layer. */
    public Optional<ErrorCatalogDto> getByInternalCode(String internalCode) {
        return Optional.ofNullable(cache.get(internalCode));
    }

    /** Convenience — returns the external message string for the given internal code. */
    public Optional<String> getMessage(String internalCode) {
        return getByInternalCode(internalCode).map(ErrorCatalogDto::message);
    }

    /** Convenience — returns HTTP status for the given internal code, or 500 if not found. */
    public int getHttpStatus(String internalCode) {
        return getByInternalCode(internalCode).map(ErrorCatalogDto::httpStatus).orElse(500);
    }

    /** Lookup by external code — use when mapping API/UI error codes inbound. */
    public Optional<ErrorCatalogDto> getByExternalCode(String externalCode) {
        return Optional.ofNullable(byExternalCode.get(externalCode));
    }

    /** Returns all errors for the given category (e.g. {@code "CERTIFICATE"}, {@code "AUTH"}). */
    public List<ErrorCatalogDto> getByCategory(String category) {
        return cache.values().stream()
                .filter(e -> e.category().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }

    /** Returns all errors with the given severity (e.g. {@code "CRITICAL"}, {@code "ERROR"}). */
    public List<ErrorCatalogDto> getBySeverity(String severity) {
        return cache.values().stream()
                .filter(e -> e.severity().equalsIgnoreCase(severity))
                .collect(Collectors.toList());
    }

    /** Returns all errors that are marked retryable. */
    public List<ErrorCatalogDto> getRetryable() {
        return cache.values().stream()
                .filter(ErrorCatalogDto::isRetryable)
                .collect(Collectors.toList());
    }

    /** Returns {@code true} if the primary (internal-code) cache contains this code. */
    public boolean containsInternalCode(String internalCode) {
        return cache.containsKey(internalCode);
    }

    // getAll(), size(), containsKey() — inherited from AbstractRefreshableCache

    // =========================================================================
    // Private helper
    // =========================================================================

    private ErrorCatalogDto toDto(ErrorCatalog row) {
        return new ErrorCatalogDto(
                row.getInternalCode(),
                row.getExternalCode(),
                row.getMessage(),
                row.getDescription(),
                row.getCategory(),
                row.getSeverity(),
                row.getHttpStatus(),
                row.isRetryable()
        );
    }
}
