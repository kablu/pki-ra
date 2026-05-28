package com.pki.ra.common.web;

import com.pki.ra.common.user.UserManagementService;
import com.pki.ra.common.user.dto.RoleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Abstract base controller providing read-only role query endpoints.
 *
 * <h3>Design Pattern — Template Method</h3>
 * Role listing endpoints are defined once here. Subclasses only declare
 * {@code @RestController} + {@code @RequestMapping}:
 *
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/admin/roles")
 * public class RoleController extends AbstractRoleController {
 *     public RoleController(UserManagementService svc) { super(svc); }
 *     // GET /roles and GET /roles/{id} are live — nothing else required.
 * }
 * }</pre>
 *
 * <h3>Why read-only?</h3>
 * Roles are seeded by Flyway migration and are rarely changed at runtime.
 * Write operations (create / update / deactivate a role) are handled directly
 * in the DB by a DBA or via a future admin-only mutation endpoint.
 *
 * @see AbstractUserController
 * @see UserManagementService
 * @author pki-ra
 * @since  1.0.0
 */
public abstract class AbstractRoleController {

    private static final Logger log = LoggerFactory.getLogger(AbstractRoleController.class);

    private final UserManagementService userManagementService;

    protected AbstractRoleController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    // =========================================================================
    // GET /roles — list all active roles
    // =========================================================================

    @GetMapping
    public final ResponseEntity<List<RoleDto>> getAllRoles() {
        List<RoleDto> roles = userManagementService.getActiveRoles();
        log.debug("GET /roles — returning {} role(s)", roles.size());
        return ResponseEntity.ok(roles);
    }

    // =========================================================================
    // GET /roles/{id}
    // =========================================================================

    @GetMapping("/{id}")
    public final ResponseEntity<RoleDto> getRoleById(@PathVariable Long id) {
        RoleDto role = userManagementService.getRoleById(id);
        return ResponseEntity.ok(role);
    }
}
