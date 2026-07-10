package com.pki.ra.raservice.registration;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for end-entity self-registration and admin activation.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   POST /api/ra/register              — end entity registers themselves (AD JWT required)
 *   PUT  /api/ra/admin/users/{id}/activate — admin activates a pending end entity (ROLE_ADMIN)
 * </pre>
 *
 * <h3>Registration flow</h3>
 * <ol>
 *   <li>End entity sends POST /api/ra/register with profileId, username, email</li>
 *   <li>AD JWT authenticates the caller — username must match the authenticated identity</li>
 *   <li>User row is created with {@code isActive=false} and ROLE_END_ENTITY assigned</li>
 *   <li>Admin calls PUT /api/ra/admin/users/{id}/activate to enable the account</li>
 *   <li>Activated user can now submit CSRs via POST /api/ra/requests</li>
 * </ol>
 */
@RestController
public class EndEntityRegistrationController {

    private final EndEntityRegistrationService registrationService;

    public EndEntityRegistrationController(EndEntityRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * Self-registration endpoint for end entities.
     *
     * <p>Requires AD authentication. The {@code username} in the request body
     * must match the authenticated principal — impersonation is rejected with 400.
     *
     * @param request       registration details
     * @param authentication injected by Spring Security from the JWT/session
     * @return 201 Created with registration details
     */
    @PostMapping("/api/ra/register")
    public ResponseEntity<EndEntityRegistrationResponse> register(
            @Valid @RequestBody EndEntityRegistrationRequest request,
            Authentication authentication) {

        String principalName = authentication.getName();
        EndEntityRegistrationResponse response = registrationService.register(request, principalName);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Admin activation endpoint — enables a pending end-entity user.
     *
     * <p>Security: protected by {@code /api/ra/admin/**} → ROLE_ADMIN rule in
     * {@link com.pki.ra.raservice.config.RaSecurityConfig}.
     *
     * @param id             user ID to activate
     * @param authentication injected admin principal (used as audit {@code activatedBy})
     * @return 204 No Content on success
     */
    @PutMapping("/api/ra/admin/users/{id}/activate")
    public ResponseEntity<Void> activateUser(
            @PathVariable Long id,
            Authentication authentication) {

        registrationService.activateUser(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
