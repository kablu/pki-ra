package com.pki.ra.raservice.registration;

import com.pki.ra.common.model.Role;
import com.pki.ra.common.model.User;
import com.pki.ra.common.model.UserRole;
import com.pki.ra.common.user.RoleRepository;
import com.pki.ra.common.user.UserRepository;
import com.pki.ra.common.user.UserRoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles end-entity self-registration.
 *
 * <p>Flow:
 * <ol>
 *   <li>Caller is authenticated via AD JWT — principal username is extracted</li>
 *   <li>Request username must match the authenticated principal (anti-impersonation)</li>
 *   <li>Username must not already exist in the users table</li>
 *   <li>User row is created with {@code isActive=false}</li>
 *   <li>{@code ROLE_END_ENTITY} is assigned to the new user</li>
 *   <li>Admin must call the activate endpoint before the user can submit CSRs</li>
 * </ol>
 */
@Slf4j
@Service
public class EndEntityRegistrationService {

    private static final String ROLE_END_ENTITY = "ROLE_END_ENTITY";

    private final UserRepository     userRepository;
    private final RoleRepository     roleRepository;
    private final UserRoleRepository userRoleRepository;

    public EndEntityRegistrationService(UserRepository userRepository,
                                        RoleRepository roleRepository,
                                        UserRoleRepository userRoleRepository) {
        this.userRepository     = userRepository;
        this.roleRepository     = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /**
     * Registers a new end entity user in inactive state.
     *
     * @param request         registration details
     * @param principalName   AD username from the authenticated JWT principal
     * @return response with the created user ID and status
     * @throws IllegalArgumentException  if username does not match principal or already exists
     * @throws IllegalStateException     if ROLE_END_ENTITY is not seeded in the roles table
     */
    @Transactional
    public EndEntityRegistrationResponse register(EndEntityRegistrationRequest request,
                                                  String principalName) {
        // Anti-impersonation: request username must match the authenticated caller
        if (!request.username().equalsIgnoreCase(principalName)) {
            throw new IllegalArgumentException(
                "Username in request '" + request.username() +
                "' does not match authenticated identity '" + principalName + "'"
            );
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException(
                "Username already registered: " + request.username()
            );
        }

        Role endEntityRole = roleRepository.findByRoleName(ROLE_END_ENTITY)
                .orElseThrow(() -> new IllegalStateException(
                    "ROLE_END_ENTITY not found in roles table — ensure it is seeded"
                ));

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .isActive(false)   // must be activated by admin before submitting CSRs
                .createdBy(request.username())
                .updatedBy(request.username())
                .build();

        userRepository.save(user);

        UserRole assignment = UserRole.builder()
                .user(user)
                .role(endEntityRole)
                .assignedBy("self-registration")
                .build();
        userRoleRepository.save(assignment);

        log.info("End entity registered (inactive): username='{}' profile='{}' userId={}",
                request.username(), request.profileId(), user.getId());

        return new EndEntityRegistrationResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            request.profileId(),
            false,
            "Registration successful. Your account is pending admin activation."
        );
    }

    /**
     * Activates a previously registered (inactive) user.
     *
     * @param userId      user ID to activate
     * @param activatedBy username of the admin performing activation
     * @throws IllegalArgumentException if user not found or already active
     */
    @Transactional
    public void activateUser(Long userId, String activatedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: id=" + userId));

        if (user.isActive()) {
            throw new IllegalArgumentException("User id=" + userId + " is already active");
        }

        user.setActive(true);
        user.setUpdatedBy(activatedBy);
        userRepository.save(user);

        log.info("End entity activated: userId={} username='{}' by='{}'",
                userId, user.getUsername(), activatedBy);
    }
}
