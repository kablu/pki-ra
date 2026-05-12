package com.pki.ra.common.error;

import com.pki.ra.common.error.dto.ErrorCatalogDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory cache of all active {@code error_catalog} rows.
 *
 * <p>Loaded once on {@link ApplicationReadyEvent} — after the full Spring
 * context is ready. Two indexes are maintained:
 * <ul>
 *   <li>Primary  — keyed by {@code internal_code} (used inside the application)</li>
 *   <li>Secondary — keyed by {@code external_code} (used for API/UI error lookup)</li>
 * </ul>
 *
 * <p>No DB calls on the request path after startup.
 * Call {@link #refresh()} to hot-reload without restarting.
 *
 * <p>Usage:
 * <pre>{@code
 * ErrorCatalogDto err = errorCatalogBean.getByInternalCode("PKI_CERT_001").orElseThrow();
 * ErrorCatalogDto err = errorCatalogBean.getByExternalCode("ERR-001").orElseThrow();
 * List<ErrorCatalogDto> certErrors = errorCatalogBean.getByCategory("CERTIFICATE");
 * }</pre>
 */
@Service
public class ErrorCatalogBean {

    private static final Logger log = LoggerFactory.getLogger(ErrorCatalogBean.class);

    private final ErrorCatalogRepository repository;

    // Primary index: internal_code → dto
    private final ConcurrentHashMap<String, ErrorCatalogDto> byInternalCode = new ConcurrentHashMap<>();

    // Secondary index: external_code → dto
    private final ConcurrentHashMap<String, ErrorCatalogDto> byExternalCode = new ConcurrentHashMap<>();

    public ErrorCatalogBean(ErrorCatalogRepository repository) {
        this.repository = repository;
    }

    // -------------------------------------------------------------------------
    // Startup loading
    // -------------------------------------------------------------------------

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void loadOnReady() {
        load();
        printTable();
    }

    private void load() {
        byInternalCode.clear();
        byExternalCode.clear();

        repository.findAllActive().forEach(row -> {
            var dto = toDto(row);
            byInternalCode.put(row.getInternalCode(), dto);
            byExternalCode.put(row.getExternalCode(), dto);
        });
    }

    // -------------------------------------------------------------------------
    // Lookup by internal code (use inside application / service layer)
    // -------------------------------------------------------------------------

    public Optional<ErrorCatalogDto> getByInternalCode(String internalCode) {
        return Optional.ofNullable(byInternalCode.get(internalCode));
    }

    /** Convenience — returns message string for the given internal code. */
    public Optional<String> getMessage(String internalCode) {
        return getByInternalCode(internalCode).map(ErrorCatalogDto::message);
    }

    /** Convenience — returns HTTP status for the given internal code, or 500 if not found. */
    public int getHttpStatus(String internalCode) {
        return getByInternalCode(internalCode).map(ErrorCatalogDto::httpStatus).orElse(500);
    }

    // -------------------------------------------------------------------------
    // Lookup by external code (use when mapping API/UI error codes)
    // -------------------------------------------------------------------------

    public Optional<ErrorCatalogDto> getByExternalCode(String externalCode) {
        return Optional.ofNullable(byExternalCode.get(externalCode));
    }

    // -------------------------------------------------------------------------
    // Filtered lookups
    // -------------------------------------------------------------------------

    /** Returns all errors for the given category (e.g. "CERTIFICATE", "AUTH"). */
    public List<ErrorCatalogDto> getByCategory(String category) {
        return byInternalCode.values().stream()
                .filter(e -> e.category().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }

    /** Returns all errors with the given severity (e.g. "CRITICAL", "ERROR"). */
    public List<ErrorCatalogDto> getBySeverity(String severity) {
        return byInternalCode.values().stream()
                .filter(e -> e.severity().equalsIgnoreCase(severity))
                .collect(Collectors.toList());
    }

    /** Returns all errors that are retryable. */
    public List<ErrorCatalogDto> getRetryable() {
        return byInternalCode.values().stream()
                .filter(ErrorCatalogDto::isRetryable)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Cache info
    // -------------------------------------------------------------------------

    public Map<String, ErrorCatalogDto> getAll() {
        return Collections.unmodifiableMap(byInternalCode);
    }

    public boolean containsInternalCode(String internalCode) {
        return byInternalCode.containsKey(internalCode);
    }

    public int size() {
        return byInternalCode.size();
    }

    // -------------------------------------------------------------------------
    // Refresh — hot-reload without restart
    // -------------------------------------------------------------------------

    /**
     * Clears both indexes and reloads all active rows from DB.
     * Use from an admin endpoint to pick up newly inserted error codes.
     *
     * @return number of entries loaded after refresh
     */
    public int refresh() {
        log.info("ErrorCatalogBean: manual refresh triggered.");
        load();
        log.info("ErrorCatalogBean: refresh complete — {} entries loaded.", byInternalCode.size());
        return byInternalCode.size();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ErrorCatalogDto toDto(com.pki.ra.common.model.ErrorCatalog row) {
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

    private void printTable() {
        if (byInternalCode.isEmpty()) {
            log.warn("ErrorCatalogBean: no active error catalog entries found.");
            return;
        }

        int intW  = byInternalCode.values().stream().mapToInt(e -> e.internalCode().length()).max().orElse(15);
        int extW  = byInternalCode.values().stream().mapToInt(e -> e.externalCode().length()).max().orElse(8);
        int catW  = byInternalCode.values().stream().mapToInt(e -> e.category().length()).max().orElse(8);
        int sevW  = byInternalCode.values().stream().mapToInt(e -> e.severity().length()).max().orElse(8);
        int msgW  = Math.min(byInternalCode.values().stream().mapToInt(e -> e.message().length()).max().orElse(20), 40);

        intW = Math.max(intW, 13);
        extW = Math.max(extW, 13);
        catW = Math.max(catW, 8);
        sevW = Math.max(sevW, 8);

        String fmt     = "| %-" + intW + "s | %-" + extW + "s | %-" + catW + "s | %-" + sevW + "s | %4s | %-" + msgW + "s |";
        String divider = "+" + "-".repeat(intW + 2) + "+" + "-".repeat(extW + 2)
                       + "+" + "-".repeat(catW + 2) + "+" + "-".repeat(sevW + 2)
                       + "+" + "-".repeat(6) + "+" + "-".repeat(msgW + 2) + "+";

        log.info("ErrorCatalogBean: {} active error entries loaded", byInternalCode.size());
        log.info(divider);
        log.info(String.format(fmt, "internal_code", "external_code", "category", "severity", "http", "message"));
        log.info(divider);

        byInternalCode.values().stream()
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
}
