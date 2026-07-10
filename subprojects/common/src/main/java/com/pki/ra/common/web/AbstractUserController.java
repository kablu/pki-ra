package com.pki.ra.common.web;

import com.pki.ra.common.user.UserManagementService;
import com.pki.ra.common.user.dto.AssignRoleRequest;
import com.pki.ra.common.user.dto.UserCreateRequest;
import com.pki.ra.common.user.dto.UserDto;
import com.pki.ra.common.user.dto.UserUpdateRequest;
import com.pki.ra.common.user.service.UserLookupService;
import com.pki.ra.common.util.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Abstract base controller providing complete user management REST endpoints.
 *
 * <h3>Design Pattern — Template Method</h3>
 * All seven user endpoints are implemented here once. Subclasses only need
 * to declare {@code @RestController} + {@code @RequestMapping} — no logic.
 *
 * <h3>Hierarchy</h3>
 * <pre>
 *   AbstractSecuredController   ← username / userId / IP resolution; auditLogService
 *   └── AbstractUserController  ← all 7 user + role endpoints
 *       └── UserController (raservice), UserController (caservice), …
 * </pre>
 *
 * <h3>Reusability</h3>
 * Any PKI module that needs user management endpoints extends this class:
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/admin/users")
 * public class UserController extends AbstractUserController {
 *     public UserController(UserManagementService svc,
 *                           AuditLogService audit,
 *                           UserLookupService lookup) {
 *         super(svc, audit, lookup);
 *     }
 *     // All 7 endpoints are live — nothing else required.
 * }
 * }</pre>
 *
 * <h3>userId resolution — single DB call per request</h3>
 * Every mutating endpoint calls {@link #resolveAuditContext(HttpServletRequest)}
 * once at the start of the method. The resolved {@code userId} is then passed to
 * both the SLF4J log statement and the {@link AuditLogService} overload that
 * accepts a pre-resolved {@code userId} — guaranteeing exactly one DB call and
 * identical values in both log and audit row.
 *
 * <h3>Audit actions written</h3>
 * <ul>
 *   <li>{@code USER_CREATE}     — POST /users</li>
 *   <li>{@code USER_UPDATE}     — PUT  /users/{id}</li>
 *   <li>{@code USER_DEACTIVATE} — POST /users/{id}/deactivate</li>
 *   <li>{@code ROLE_ASSIGN}     — POST /users/{id}/roles</li>
 *   <li>{@code ROLE_REMOVE}     — DELETE /users/{id}/roles/{roleId}</li>
 * </ul>
 *
 * @see AbstractSecuredController
 * @see AbstractRoleController
 * @see UserManagementService
 * @author pki-ra
 * @since  1.0.0
 */
public abstract class AbstractUserController extends AbstractSecuredController {

    private static final Logger log = LoggerFactory.getLogger(AbstractUserController.class);

    // Audit action constants
    private static final String ACTION_USER_CREATE     = "USER_CREATE";
    private static final String ACTION_USER_UPDATE     = "USER_UPDATE";
    private static final String ACTION_USER_DEACTIVATE = "USER_DEACTIVATE";
    private static final String ACTION_ROLE_ASSIGN     = "ROLE_ASSIGN";
    private static final String ACTION_ROLE_REMOVE     = "ROLE_REMOVE";

    private final UserManagementService userManagementService;

    /**
     * Constructor for subclasses — receives all three services from Spring.
     *
     * @param userManagementService handles user CRUD + role assignment business logic
     * @param auditLogService       writes SUCCESS / FAILURE audit entries
     * @param userLookupService     resolves numeric userId from authenticated username
     */
    protected AbstractUserController(UserManagementService userManagementService,
                                     AuditLogService auditLogService,
                                     UserLookupService userLookupService) {
        super(auditLogService, userLookupService);
        this.userManagementService = userManagementService;
    }

    // =========================================================================
    // GET /users — list all (read-only, no audit)
    // =========================================================================

    @GetMapping
    public final ResponseEntity<List<UserDto>> getAllUsers() {
        List<UserDto> users = userManagementService.getAllUsers();
        log.debug("GET /users — returning {} user(s)", users.size());
        return ResponseEntity.ok(users);
    }

    // =========================================================================
    // GET /users/{id} (read-only, no audit)
    // =========================================================================

    @GetMapping("/{id}")
    public final ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userManagementService.getUserById(id));
    }

    // =========================================================================
    // POST /users — create
    // =========================================================================

    @PostMapping
    public final ResponseEntity<UserDto> createUser(
            @Valid @RequestBody UserCreateRequest request,
            HttpServletRequest httpRequest) {

        AuditContext ctx      = resolveAuditContext(httpRequest);
        String       resource = request.username();

        try {
            UserDto created = userManagementService.createUser(request, ctx.username());

            auditLogService.logSuccess(ctx.username(), ACTION_USER_CREATE, resource,
                    "User created: " + created.username(), ctx.ip(), ctx.userId());

            log.info("[USER_CREATE] username='{}' by='{}' userId='{}' ip='{}'",
                     resource, ctx.username(), ctx.userId(), ctx.ip());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);

        } catch (Exception ex) {
            auditLogService.logFailure(ctx.username(), ACTION_USER_CREATE, resource,
                    "Create failed: " + ex.getMessage(), ctx.ip(), ctx.userId());

            log.error("[USER_CREATE] FAILED username='{}' by='{}' userId='{}' reason='{}'",
                      resource, ctx.username(), ctx.userId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // =========================================================================
    // PUT /users/{id} — update profile
    // =========================================================================

    @PutMapping("/{id}")
    public final ResponseEntity<UserDto> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request,
            HttpServletRequest httpRequest) {

        AuditContext ctx      = resolveAuditContext(httpRequest);
        String       resource = String.valueOf(id);

        try {
            UserDto updated = userManagementService.updateUser(id, request, ctx.username());

            auditLogService.logSuccess(ctx.username(), ACTION_USER_UPDATE, resource,
                    "User updated: " + updated.username(), ctx.ip(), ctx.userId());

            log.info("[USER_UPDATE] userId={} by='{}' actorId='{}' ip='{}'",
                     id, ctx.username(), ctx.userId(), ctx.ip());
            return ResponseEntity.ok(updated);

        } catch (Exception ex) {
            auditLogService.logFailure(ctx.username(), ACTION_USER_UPDATE, resource,
                    "Update failed: " + ex.getMessage(), ctx.ip(), ctx.userId());

            log.error("[USER_UPDATE] FAILED userId={} by='{}' actorId='{}' reason='{}'",
                      id, ctx.username(), ctx.userId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // =========================================================================
    // POST /users/{id}/deactivate — soft delete
    // =========================================================================

    @PostMapping("/{id}/deactivate")
    public final ResponseEntity<Void> deactivateUser(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        AuditContext ctx      = resolveAuditContext(httpRequest);
        String       resource = String.valueOf(id);

        try {
            userManagementService.deactivateUser(id, ctx.username());

            auditLogService.logSuccess(ctx.username(), ACTION_USER_DEACTIVATE, resource,
                    "User deactivated: id=" + id, ctx.ip(), ctx.userId());

            log.info("[USER_DEACTIVATE] userId={} by='{}' actorId='{}' ip='{}'",
                     id, ctx.username(), ctx.userId(), ctx.ip());
            return ResponseEntity.noContent().build();

        } catch (Exception ex) {
            auditLogService.logFailure(ctx.username(), ACTION_USER_DEACTIVATE, resource,
                    "Deactivate failed: " + ex.getMessage(), ctx.ip(), ctx.userId());

            log.error("[USER_DEACTIVATE] FAILED userId={} by='{}' actorId='{}' reason='{}'",
                      id, ctx.username(), ctx.userId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // =========================================================================
    // POST /users/{id}/roles — assign role
    // =========================================================================

    @PostMapping("/{id}/roles")
    public final ResponseEntity<UserDto> assignRole(
            @PathVariable Long id,
            @Valid @RequestBody AssignRoleRequest request,
            HttpServletRequest httpRequest) {

        AuditContext ctx      = resolveAuditContext(httpRequest);
        String       resource = "userId=" + id + " roleId=" + request.roleId();

        try {
            UserDto updated = userManagementService.assignRole(id, request, ctx.username());

            auditLogService.logSuccess(ctx.username(), ACTION_ROLE_ASSIGN, resource,
                    "Role assigned to user: " + resource, ctx.ip(), ctx.userId());

            log.info("[ROLE_ASSIGN] {} by='{}' actorId='{}' ip='{}'",
                     resource, ctx.username(), ctx.userId(), ctx.ip());
            return ResponseEntity.ok(updated);

        } catch (Exception ex) {
            auditLogService.logFailure(ctx.username(), ACTION_ROLE_ASSIGN, resource,
                    "Assign role failed: " + ex.getMessage(), ctx.ip(), ctx.userId());

            log.error("[ROLE_ASSIGN] FAILED {} by='{}' actorId='{}' reason='{}'",
                      resource, ctx.username(), ctx.userId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // =========================================================================
    // DELETE /users/{id}/roles/{roleId} — remove role
    // =========================================================================

    @DeleteMapping("/{id}/roles/{roleId}")
    public final ResponseEntity<UserDto> removeRole(
            @PathVariable Long id,
            @PathVariable Long roleId,
            HttpServletRequest httpRequest) {

        AuditContext ctx      = resolveAuditContext(httpRequest);
        String       resource = "userId=" + id + " roleId=" + roleId;

        try {
            UserDto updated = userManagementService.removeRole(id, roleId, ctx.username());

            auditLogService.logSuccess(ctx.username(), ACTION_ROLE_REMOVE, resource,
                    "Role removed from user: " + resource, ctx.ip(), ctx.userId());

            log.info("[ROLE_REMOVE] {} by='{}' actorId='{}' ip='{}'",
                     resource, ctx.username(), ctx.userId(), ctx.ip());
            return ResponseEntity.ok(updated);

        } catch (Exception ex) {
            auditLogService.logFailure(ctx.username(), ACTION_ROLE_REMOVE, resource,
                    "Remove role failed: " + ex.getMessage(), ctx.ip(), ctx.userId());

            log.error("[ROLE_REMOVE] FAILED {} by='{}' actorId='{}' reason='{}'",
                      resource, ctx.username(), ctx.userId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
