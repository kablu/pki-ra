package com.pki.ra.common.user;

import com.pki.ra.common.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link User} entities.
 *
 * <p>Used by {@link UserManagementService} for all user CRUD operations,
 * and by {@link com.pki.ra.common.util.AuditLogService} to resolve
 * a username string to a numeric {@code userId} for audit entries.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Find by AD sAMAccountName — primary lookup from Spring Security principal. */
    Optional<User> findByUsername(String username);

    /** Check existence by username — used before create to enforce uniqueness. */
    boolean existsByUsername(String username);

    /** All active (non-deleted) users, ordered by username. */
    @Query("SELECT u FROM User u WHERE u.isActive = true ORDER BY u.username")
    List<User> findAllActive();

    /** Active users who hold a specific role name — for admin dashboards. */
    @Query("""
            SELECT u FROM User u
            JOIN   u.userRoles ur
            JOIN   ur.role     r
            WHERE  u.isActive = true
            AND    r.roleName  = :roleName
            ORDER BY u.username
           """)
    List<User> findActiveByRoleName(String roleName);
}
