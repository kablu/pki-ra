package com.pki.ra.raservice.controller;

import com.pki.ra.common.user.UserManagementService;
import com.pki.ra.common.web.AbstractRoleController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role query REST endpoints for the RA service.
 *
 * <p>All endpoints are fully implemented in {@link AbstractRoleController} —
 * this class only mounts them at the correct URL prefix for this module.
 *
 * <h3>Inherited endpoints</h3>
 * <ul>
 *   <li>{@code GET /api/admin/roles}      — list all active roles</li>
 *   <li>{@code GET /api/admin/roles/{id}} — get role by ID</li>
 * </ul>
 *
 * <p>Security: all endpoints require {@code ROLE_ADMIN} via the admin security filter chain.
 *
 * @see AbstractRoleController
 * @author pki-ra
 * @since  1.0.0
 */
@RestController
@RequestMapping("/api/admin/roles")
public class RoleController extends AbstractRoleController {

    public RoleController(UserManagementService userManagementService) {
        super(userManagementService);
    }
}
