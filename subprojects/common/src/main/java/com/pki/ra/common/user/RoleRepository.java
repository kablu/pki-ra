package com.pki.ra.common.user;

import com.pki.ra.common.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Role} entities.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /** Lookup by exact role name (e.g. {@code ROLE_ADMIN}). */
    Optional<Role> findByRoleName(String roleName);

    /** All active roles, ordered by name — used to populate assignment dropdowns. */
    @Query("SELECT r FROM Role r WHERE r.isActive = true ORDER BY r.roleName")
    List<Role> findAllActive();

    /** Check existence by role name — guards against duplicate seed inserts. */
    boolean existsByRoleName(String roleName);
}
