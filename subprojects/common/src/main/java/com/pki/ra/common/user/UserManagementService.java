package com.pki.ra.common.user;

import com.pki.ra.common.model.Role;
import com.pki.ra.common.model.User;
import com.pki.ra.common.model.UserRole;
import com.pki.ra.common.user.dto.AssignRoleRequest;
import com.pki.ra.common.user.dto.RoleDto;
import com.pki.ra.common.user.dto.UserCreateRequest;
import com.pki.ra.common.user.dto.UserDto;
import com.pki.ra.common.user.dto.UserUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Core service for all user and role management operations.
 *
 * <h3>Reusability</h3>
 * Declared in {@code common} — every PKI module (raservice, cmpservice,
 * acmeservice, online) can inject this bean directly.
 * {@link com.pki.ra.common.web.AbstractUserController} and
 * {@link com.pki.ra.common.web.AbstractRoleController} depend on this service,
 * so any module that extends those controllers gets full user/role management
 * with zero logic duplication.
 *
 * <h3>Audit integration</h3>
 * This service does NOT write audit log entries — that is the responsibility
 * of the controllers (via {@link com.pki.ra.common.util.AuditLogService}).
 * Separation keeps service methods testable without audit side effects.
 *
 * <h3>resolveUserId</h3>
 * Used by {@link com.pki.ra.common.util.AuditLogService} to enrich
 * {@code audit_log.user_id} from a username string.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class UserManagementService {

    private final UserRepository     userRepository;
    private final RoleRepository     roleRepository;
    private final UserRoleRepository userRoleRepository;

    public UserManagementService(UserRepository userRepository,
                                 RoleRepository roleRepository,
                                 UserRoleRepository userRoleRepository) {
        this.userRepository     = userRepository;
        this.roleRepository     = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    // =========================================================================
    // User CRUD
    // =========================================================================

    /**
     * Returns all users as DTOs (active and inactive).
     */
    public List<UserDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserDto::from)
                .toList();
    }

    /**
     * Returns all active users only.
     */
    public List<UserDto> getActiveUsers() {
        return userRepository.findAllActive()
                .stream()
                .map(UserDto::from)
                .toList();
    }

    /**
     * Returns a single user by ID.
     *
     * @throws IllegalArgumentException if not found
     */
    public UserDto getUserById(Long id) {
        return userRepository.findById(id)
                .map(UserDto::from)
                .orElseThrow(() -> new IllegalArgumentException("User not found: id=" + id));
    }

    /**
     * Returns a user by AD username.
     */
    public Optional<UserDto> getUserByUsername(String username) {
        return userRepository.findByUsername(username).map(UserDto::from);
    }

    /**
     * Creates a new user and optionally assigns initial roles in one transaction.
     *
     * @param request   user data + optional roleIds list
     * @param createdBy username of the admin performing the action
     * @return created user as {@link UserDto}
     * @throws IllegalStateException if username already exists
     */
    @Transactional
    public UserDto createUser(UserCreateRequest request, String createdBy) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalStateException("Username already exists: " + request.username());
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .fullName(request.fullName())
                .displayName(request.displayName())
                .isActive(true)
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();

        userRepository.save(user);

        // Assign initial roles if provided
        if (request.roleIds() != null) {
            for (Long roleId : request.roleIds()) {
                assignRoleInternal(user, roleId, createdBy);
            }
        }

        log.info("User created: username='{}' by='{}'", user.getUsername(), createdBy);
        return UserDto.from(userRepository.findById(user.getId()).orElseThrow());
    }

    /**
     * Updates an existing user's profile fields.
     * Role changes are handled by {@link #assignRole} / {@link #removeRole}.
     *
     * @throws IllegalArgumentException if user not found
     */
    @Transactional
    public UserDto updateUser(Long id, UserUpdateRequest request, String updatedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: id=" + id));

        if (request.email()       != null) user.setEmail(request.email());
        if (request.fullName()    != null) user.setFullName(request.fullName());
        if (request.displayName() != null) user.setDisplayName(request.displayName());
        if (request.isActive()    != null) user.setActive(request.isActive());

        user.setUpdatedBy(updatedBy);
        user.setUpdatedAt(Instant.now());

        log.info("User updated: id={} by='{}'", id, updatedBy);
        return UserDto.from(userRepository.save(user));
    }

    /**
     * Soft-deletes a user by setting {@code isActive = false}.
     * All role assignments are preserved for audit purposes.
     *
     * @throws IllegalArgumentException if user not found
     */
    @Transactional
    public void deactivateUser(Long id, String deactivatedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: id=" + id));

        user.setActive(false);
        user.setUpdatedBy(deactivatedBy);
        user.setUpdatedAt(Instant.now());

        userRepository.save(user);
        log.info("User deactivated: id={} by='{}'", id, deactivatedBy);
    }

    /**
     * Finds an existing user by username, or creates a new one.
     * Used on first AD login to auto-provision the user row.
     *
     * @param username  AD sAMAccountName
     * @param createdBy typically the username itself (self-provisioning on login)
     * @return resolved or newly created user ID
     */
    @Transactional
    public Long findOrCreate(String username, String createdBy) {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .username(username)
                            .isActive(true)
                            .createdBy(createdBy)
                            .updatedBy(createdBy)
                            .build();
                    userRepository.save(newUser);
                    log.info("Auto-provisioned user on first login: username='{}'", username);
                    return newUser.getId();
                });
    }

    /**
     * Records a successful login timestamp for a user.
     * Called by the security layer after successful AD authentication.
     */
    @Transactional
    public void recordLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
        });
    }

    // =========================================================================
    // Role management
    // =========================================================================

    /**
     * Returns all roles as DTOs (active and inactive).
     */
    public List<RoleDto> getAllRoles() {
        return roleRepository.findAll()
                .stream()
                .map(RoleDto::from)
                .toList();
    }

    /**
     * Returns all active roles.
     */
    public List<RoleDto> getActiveRoles() {
        return roleRepository.findAllActive()
                .stream()
                .map(RoleDto::from)
                .toList();
    }

    /**
     * Returns a single role by ID.
     *
     * @throws IllegalArgumentException if not found
     */
    public RoleDto getRoleById(Long id) {
        return roleRepository.findById(id)
                .map(RoleDto::from)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: id=" + id));
    }

    /**
     * Assigns a role to a user.
     * Idempotent — if the role is already assigned, returns current state without error.
     *
     * @throws IllegalArgumentException if user or role not found
     */
    @Transactional
    public UserDto assignRole(Long userId, AssignRoleRequest request, String assignedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: id=" + userId));

        assignRoleInternal(user, request.roleId(), assignedBy);
        return UserDto.from(userRepository.findById(userId).orElseThrow());
    }

    /**
     * Removes a role from a user.
     *
     * @throws IllegalArgumentException if user, role, or the assignment is not found
     */
    @Transactional
    public UserDto removeRole(Long userId, Long roleId, String removedBy) {
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: id=" + userId));

        UserRole assignment = userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Role assignment not found: userId=" + userId + " roleId=" + roleId));

        userRoleRepository.delete(assignment);
        log.info("Role removed: userId={} roleId={} by='{}'", userId, roleId, removedBy);
        return UserDto.from(userRepository.findById(userId).orElseThrow());
    }

    // =========================================================================
    // AuditLogService integration — resolve userId from username
    // =========================================================================

    /**
     * Resolves a numeric {@code userId} from an AD username string.
     *
     * <p>Called by {@link com.pki.ra.common.util.AuditLogService} to enrich
     * {@code audit_log.user_id} on every log entry.
     *
     * <p>Returns {@link Optional#empty()} for {@code "system"} and
     * {@code "anonymous"} — these have no row in the users table.
     *
     * @param username AD sAMAccountName
     * @return user ID, or empty if no matching user
     */
    public Optional<Long> resolveUserId(String username) {
        if (username == null
                || username.equals("system")
                || username.equals("anonymous")
                || username.equals("anonymousUser")) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username).map(User::getId);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void assignRoleInternal(User user, Long roleId, String assignedBy) {
        if (userRoleRepository.existsByUserIdAndRoleId(user.getId(), roleId)) {
            log.debug("Role already assigned — skipping: userId={} roleId={}", user.getId(), roleId);
            return;
        }

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: id=" + roleId));

        UserRole assignment = UserRole.builder()
                .user(user)
                .role(role)
                .assignedBy(assignedBy)
                .build();

        userRoleRepository.save(assignment);
        log.info("Role assigned: userId={} role='{}' by='{}'", user.getId(), role.getRoleName(), assignedBy);
    }
}
