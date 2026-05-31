package com.pki.ra.common.config.dto;

import java.time.Instant;

/**
 * Universal response record returned by every {@code POST /refresh} endpoint
 * across all services (RA, CMP, ACME, Online).
 *
 * <h3>Why one shared record — not per-service DTOs?</h3>
 * Every cache refresh produces the same information regardless of which
 * service performed it. One shared type means:
 * <ul>
 *   <li>Admin dashboards receive identical JSON from every refresh endpoint.</li>
 *   <li>A future "refresh all caches" endpoint returns {@code List<RefreshResult>}.</li>
 *   <li>No per-service DTO maintenance (no CmpRefreshResponse, AcmeRefreshResponse…).</li>
 * </ul>
 *
 * <h3>Replaces</h3>
 * {@code ConfigRefreshResponse} — which was tied to RA app-config domain only.
 *
 * @param serviceName  module name from {@code spring.application.name},
 *                     e.g. {@code "pki-ra-raservice"}, {@code "pki-ra-caservice"}.
 *                     Never hardcoded — injected via {@code @Value} in each bean.
 * @param resourceId   DB table name read from the {@code @Table} annotation on
 *                     the JPA entity that backs the cache,
 *                     e.g. {@code "app_config"}, {@code "cmp_config"}.
 * @param count        number of entries in the cache after reload
 * @param refreshedAt  UTC instant when the reload completed
 * @param triggeredBy  username of the admin, or {@code "system"} for
 *                     scheduler / programmatic triggers
 *
 * @see com.pki.ra.common.config.Refreshable
 * @see com.pki.ra.common.web.AbstractRefreshController
 */
public record RefreshResult(
        String  serviceName,
        String  resourceId,
        int     count,
        Instant refreshedAt,
        String  triggeredBy
) {}
