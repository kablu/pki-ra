package com.pki.ra.common.user;

import com.pki.ra.common.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link UserRole} junction entities.
 *
 * <p>Most operations go through {@link UserManagementService} which uses
 * {@link UserRepository} (with cascaded {@code UserRole} collection).
 * This repository is used for targeted single-assignment lookups.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /** Lookup one assignment — used to check before assign / to locate before remove. */
    Optional<UserRole> findByUserIdAndRoleId(Long userId, Long roleId);

    /** Guard: true if this exact user→role pair already exists. */
    boolean existsByUserIdAndRoleId(Long userId, Long roleId);
}
