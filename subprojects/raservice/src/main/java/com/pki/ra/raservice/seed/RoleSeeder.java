package com.pki.ra.raservice.seed;

import com.pki.ra.common.model.Role;
import com.pki.ra.common.user.RoleRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the built-in roles for local H2 development.
 *
 * <p>In production, these rows are inserted by the Flyway V1 migration.
 * This seeder only activates under the {@code h2} profile.
 */
@Component
@Profile("h2")
@Order(2)
public class RoleSeeder extends AbstractH2Seeder {

    private final RoleRepository roleRepository;

    public RoleSeeder(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    protected long count() {
        return roleRepository.count();
    }

    @Override
    protected void seed() {
        roleRepository.saveAll(List.of(
            role("ROLE_ADMIN",      "Full system access — user management, config, approvals"),
            role("ROLE_OPERATOR",   "Certificate operations — CSR review, approve, reject"),
            role("ROLE_AUDITOR",    "Read-only access to audit logs and request history"),
            role("ROLE_VIEWER",     "Read-only access to configs and status dashboards"),
            role("ROLE_END_ENTITY", "Self-registered end user — can submit CSRs for own certificates")
        ));
    }

    private Role role(String name, String description) {
        return Role.builder()
                .roleName(name)
                .description(description)
                .isActive(true)
                .build();
    }
}
