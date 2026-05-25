package com.pki.ra.common.config.dto;

import java.time.Instant;

/**
 * Response payload returned by POST /api/admin/config/refresh.
 *
 * @param count        number of active rows now in the cache
 * @param refreshedAt  UTC instant when the reload completed
 * @param triggeredBy  username (or "system") that initiated the refresh
 */
public record ConfigRefreshResponse(
        int     count,
        Instant refreshedAt,
        String  triggeredBy
) {}
