package com.pki.ra.common.web;

import com.pki.ra.common.user.UserManagementService;
import com.pki.ra.common.user.dto.AssignRoleRequest;
import com.pki.ra.common.user.dto.UserCreateRequest;
import com.pki.ra.common.user.dto.UserDto;
import com.pki.ra.common.user.dto.UserUpdateRequest;
import com.pki.ra.common.util.AuditLogService;
import com.pki.ra.common.util.IpAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Abstract base controller providing complete user management REST endpoints.
 *
 * <h3>Design Pattern — Template Method</h3>
 * All seven user endpoints are implemented here once. Subclasses only need
 * to declare {@code @RestController} + {@code @RequestMapping} — no logic.
 *
 * <h3>Reusability</h3>
 * Any PKI module that needs user management endpoints extends this class:
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/admin/users")
 * public class UserController extends AbstractUserController {
 *     public UserController(UserManagementService svc, AuditLogService audit) {
 *         super(svc, audit);
 *     }
 *     // All 7 endpoints are live — nothing else required.
 * }
 * }</pre>
 *
 * <h3>Authentication resolution</h3>
 * Uses {@link SecurityContextHolder} (not method parameter injection) because
 * Spring MVC's {@code HandlerMethodArgumentResolver} does not reliably inject
 * {@code Authentication} into inherited abstract-class methods.
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
 * @see AbstractRoleController
 * @see UserManagementService
 * @author pki-ra
 * @since  1.0.0
 */
public abstract class AbstractUserController {

    private static final Logger log = LoggerFactory.getLogger(AbstractUserController.class);

    // Audit action constants
    private static final String ACTION_USER_CREATE     = "USER_CREATE";
    private static final String ACTION_USER_UPDATE     = "USER_UPDATE";
    private static final String ACTION_USER_DEACTIVATE = "USER_DEACTIVATE";
    private static final String ACTION_ROLE_ASSIGN     = "ROLE_ASSIGN";
    private static final String ACTION_ROLE_REMOVE     = "ROLE_REMOVE";

    private final UserManagementService userManagementService;
    private final AuditLogService       auditLogService;

    /**
     * Constructor for subclasses — receives both services from Spring.
     */
    protected AbstractUserController(UserManagementService userManagementService,
                                     AuditLogService auditLogService) {
        this.userManagementService = userManagementService;
        this.auditLogService       = auditLogService;
    }

    // =========================================================================
    // GET /users — list all
    // =========================================================================

    @GetMapping
    public final ResponseEntity<List<UserDto>> getAllUsers() {
        List<UserDto> users = userManagementService.getAllUsers();
        log.debug("GET /users — returning {} user(s)", users.size());
        return ResponseEntity.ok(users);
    }

    // =========================================================================
    // GET /users/{id}
    // =========================================================================

    @GetMapping("/{id}")
    public final ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        UserDto user = userManagementService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    // =========================================================================
    // POST /users — create
    // =========================================================================

    @PostMapping
    public final ResponseEntity<UserDto> createUser(
            @Valid @RequestBody UserCreateRequest request,
            HttpServletRequest httpRequest) {

        String actor = resolveUsername();
        String ip    = resolveClientIp(httpRequest);
        String resource = request.username();

        try {
            UserDto created = userManagementService.createUser(request, actor);
            auditLogService.logSuccess(actor, ACTION_USER_CREATE, resource,
                    "User created: " + created.username(), ip);
            log.info("[USER_CREATE] username='{}' by='{}' ip='{}'", resource, actor, ip);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);

        } catch (Exception ex) {
            auditLogService.logFailure(actor, ACTION_USER_CREATE, resource,
                    "Create failed: " + ex.getMessage(), ip);
            log.error("[USER_CREATE] FAILED username='{}' reason='{}'", resource, ex.getMessage(), ex);
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

        String actor    = resolveUsername();
        String ip       = resolveClientIp(httpRequest);
        String resource = String.valueOf(id);

        try {
            UserDto updated = userManagementService.updateUser(id, request, actor);
            auditLogService.logSuccess(actor, ACTION_USER_UPDATE, resource,
                    "User updated: " + updated.username(), ip);
            return ResponseEntity.ok(updated);

        } catch (Exception ex) {
            auditLogService.logFailure(actor, ACTION_USER_UPDATE, resource,
                    "Update failed: " + ex.getMessage(), ip);
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

        String actor    = resolveUsername();
        String ip       = resolveClientIp(httpRequest);
        String resource = String.valueOf(id);

        try {
            userManagementService.deactivateUser(id, actor);
            auditLogService.logSuccess(actor, ACTION_USER_DEACTIVATE, resource,
                    "User deactivated: id=" + id, ip);
            log.info("[USER_DEACTIVATE] id={} by='{}' ip='{}'", id, actor, ip);
            return ResponseEntity.noContent().build();

        } catch (Exception ex) {
            auditLogService.logFailure(actor, ACTION_USER_DEACTIVATE, resource,
                    "Deactivate failed: " + ex.getMessage(), ip);
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

        String actor    = resolveUsername();
        String ip       = resolveClientIp(httpRequest);
        String resource = "userId=" + id + " roleId=" + request.roleId();

        try {
            UserDto updated = userManagementService.assignRole(id, request, actor);
            auditLogService.logSuccess(actor, ACTION_ROLE_ASSIGN, resource,
                    "Role assigned to user: " + resource, ip);
            return ResponseEntity.ok(updated);

        } catch (Exception ex) {
            auditLogService.logFailure(actor, ACTION_ROLE_ASSIGN, resource,
                    "Assign role failed: " + ex.getMessage(), ip);
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

        String actor    = resolveUsername();
        String ip       = resolveClientIp(httpRequest);
        String resource = "userId=" + id + " roleId=" + roleId;

        try {
            UserDto updated = userManagementService.removeRole(id, roleId, actor);
            auditLogService.logSuccess(actor, ACTION_ROLE_REMOVE, resource,
                    "Role removed from user: " + resource, ip);
            return ResponseEntity.ok(updated);

        } catch (Exception ex) {
            auditLogService.logFailure(actor, ACTION_ROLE_REMOVE, resource,
                    "Remove role failed: " + ex.getMessage(), ip);
            throw ex;
        }
    }

    // =========================================================================
    // Private helpers — same pattern as AbstractRefreshController
    // =========================================================================

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        log.warn("No authenticated principal in SecurityContext — using 'anonymous'");
        return "anonymous";
    }

    private String resolveClientIp(HttpServletRequest request) {
        return IpAddressResolver.resolve(request);
    }
}
