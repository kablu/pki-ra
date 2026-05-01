package com.pki.ra.common.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Supplies the current AD username to Spring Data JPA's auditing mechanism.
 *
 * <p>Spring JPA Auditing uses this bean to populate {@code @CreatedBy} and
 * {@code @LastModifiedBy} columns on all entities that extend {@link com.pki.ra.common.model.BaseAuditEntity}.
 *
 * <h3>Resolution order</h3>
 * <ol>
 *   <li>Authenticated, non-anonymous Spring Security principal → {@code principal.getName()} (AD sAMAccountName)</li>
 *   <li>No authentication context (scheduled jobs, migrations) → {@code "system"}</li>
 * </ol>
 */
@Component("auditorAwareImpl")
public class AuditorAwareImpl implements AuditorAware<String> {

    private static final String SYSTEM_USER   = "system";
    private static final String ANONYMOUS_USER = "anonymousUser";

    /**
     * Returns the current auditor username.
     * Never returns {@link Optional#empty()} — falls back to {@value #SYSTEM_USER}.
     */
    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .filter(name -> !ANONYMOUS_USER.equals(name))
                .or(() -> Optional.of(SYSTEM_USER));
    }
}
