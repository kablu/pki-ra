package com.pki.ra.common.config.dto;

/**
 * Immutable snapshot of one {@code app_config} row.
 * Callers read {@link #configValue()} and parse it as needed.
 */
public record AppConfigDto(
        String  configKey,
        String  configType,
        String  configValue,
        boolean isActive
) {}
