package com.pki.ra.raservice.controller;

import com.pki.ra.common.user.UserManagementService;
import com.pki.ra.common.util.AuditLogService;
import com.pki.ra.common.web.AbstractUserController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User management REST endpoints for the RA service.
 *
 * <p>All seven endpoints are fully implemented in
 * {@link AbstractUserController} — this class only mounts them
 * at the correct URL prefix for this module.
 *
 * <h3>Inherited endpoints</h3>
 * <ul>
 *   <li>{@code GET    /api/admin/users}                     — list all users</li>
 *   <li>{@code GET    /api/admin/users/{id}}                — get user by ID</li>
 *   <li>{@code POST   /api/admin/users}                     — create user</li>
 *   <li>{@code PUT    /api/admin/users/{id}}                — update user profile</li>
 *   <li>{@code POST   /api/admin/users/{id}/deactivate}     — soft-delete user</li>
 *   <li>{@code POST   /api/admin/users/{id}/roles}          — assign role</li>
 *   <li>{@code DELETE /api/admin/users/{id}/roles/{roleId}} — remove role</li>
 * </ul>
 *
 * <p>Security: all endpoints require {@code ROLE_ADMIN} via the admin security filter chain.
 *
 * @see AbstractUserController
 * @author pki-ra
 * @since  1.0.0
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserController extends AbstractUserController {

    public UserController(UserManagementService userManagementService,
                          AuditLogService auditLogService) {
        super(userManagementService, auditLogService);
    }
}
