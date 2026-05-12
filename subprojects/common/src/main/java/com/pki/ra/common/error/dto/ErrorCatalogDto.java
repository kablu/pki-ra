package com.pki.ra.common.error.dto;

/**
 * Immutable snapshot of one {@code error_catalog} row.
 */
public record ErrorCatalogDto(
        String  internalCode,
        String  externalCode,
        String  message,
        String  description,
        String  category,
        String  severity,
        int     httpStatus,
        boolean isRetryable
) {}
