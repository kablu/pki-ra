package com.pki.ra.raservice.controller;

import com.pki.ra.common.config.ConfigBean;
import com.pki.ra.common.config.dto.ConfigRefreshResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST endpoint for hot-reloading the {@link ConfigBean} cache.
 *
 * <p>Full flow:
 * <ol>
 *   <li>Admin updates rows in {@code app_config} via DB tool or migration script.</li>
 *   <li>Admin calls {@code POST /api/admin/config/refresh}.</li>
 *   <li>This controller delegates to {@link ConfigBean#refresh(String)}.</li>
 *   <li>ConfigBean clears its in-memory cache and reloads all active rows from DB.</li>
 *   <li>Response returns count, refreshedAt, and triggeredBy — no restart required.</li>
 * </ol>
 *
 * <p>Security: protected by {@code ROLE_ADMIN} via {@code AdminSecurityConfig}.
 * Unauthorized callers receive {@code 401}; authenticated non-admins receive {@code 403}.
 */
@RestController
@RequestMapping("/api/admin/config")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    private final ConfigBean configBean;

    public ConfigController(ConfigBean configBean) {
        this.configBean = configBean;
    }

    /**
     * Clears and reloads the {@link ConfigBean} cache from the database.
     *
     * <p>The authenticated principal's username is recorded as {@code triggeredBy}
     * in the response so the caller can confirm who initiated the refresh.
     *
     * @param authentication injected by Spring Security — always present (filter enforces auth)
     * @return 200 OK with {@link ConfigRefreshResponse}
     */
    @PostMapping("/refresh")
    public ResponseEntity<ConfigRefreshResponse> refresh(Authentication authentication) {
        String triggeredBy = authentication.getName();
        log.info("Config refresh requested by '{}'", triggeredBy);

        ConfigRefreshResponse response = configBean.refresh(triggeredBy);

        log.info("Config refresh complete — {} entries loaded, triggeredBy='{}'",
                 response.count(), response.triggeredBy());

        return ResponseEntity.ok(response);
    }
}
