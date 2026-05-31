package com.pki.ra.common.util;

import com.pki.ra.common.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for user lookup operations used by {@link UserLookupService}.
 *
 * <p>Kept in the {@code util} package (alongside {@link UserLookupService})
 * rather than a dedicated {@code user} package — this is an audit-support
 * repository, not a full user-management repository.
 * Full user CRUD lives in {@code UserRepository} (user-management branch).
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Repository
public interface UserLookupRepository extends JpaRepository<User, Long> {

    /**
     * Finds an active user by their AD sAMAccountName.
     *
     * <p>Only active users ({@code is_active = true}) are matched —
     * deactivated users should not be linked to new audit entries.
     *
     * @param username AD sAMAccountName, e.g. {@code "john.doe"}
     * @return the matching active user, or empty if not found / deactivated
     */
    Optional<User> findByUsernameAndIsActiveTrue(String username);
}
