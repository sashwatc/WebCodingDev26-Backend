package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.SignInRequest;
import com.FBLA.WebCodingDev26Backend.dto.SignInResponse;
import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.ConflictException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.service.AppwriteAuthService;
import com.FBLA.WebCodingDev26Backend.service.AuthService;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication and identity endpoints.
 *
 * <p>Serves the base route {@code /api/auth}: account lookup, current-user resolution,
 * sign in / sign up / sign out, and password-reset initiation. The backend is
 * stateless — identity is resolved per request from a verified Appwrite session or,
 * in demo mode, the {@code X-Demo-User-Email} header (via {@link DemoAuthorizationService}).</p>
 *
 * <p>Collaborators:
 * <ul>
 *   <li>{@link AuthService} — user persistence/lookup and sign-in/sign-up logic.</li>
 *   <li>{@link DemoAuthorizationService} — resolves the caller's verified email/role.</li>
 *   <li>{@link AppwriteAuthService} — optional; triggers Appwrite-side password recovery
 *       emails when configured (may be null, e.g. in tests / pure demo mode).</li>
 * </ul></p>
 */
@RestController // REST controller: handler return values are serialized to the response body
@RequestMapping("/api/auth") // base route for all auth endpoints
public class AuthController {
    // User account persistence and sign-in/sign-up business logic.
    private final AuthService service;
    // Resolves the caller's identity/role and provides staff/admin checks.
    private final DemoAuthorizationService authorizationService;
    // Optional Appwrite integration for password-recovery emails; null when not configured.
    private final AppwriteAuthService appwriteAuthService;

    /** Primary (Spring-injected) constructor wiring all three collaborators. */
    @org.springframework.beans.factory.annotation.Autowired
    public AuthController(AuthService service, DemoAuthorizationService authorizationService, AppwriteAuthService appwriteAuthService) {
        this.service = service;
        this.authorizationService = authorizationService;
        this.appwriteAuthService = appwriteAuthService;
    }

    // Package-private constructor for test compatibility
    // Delegates to the primary constructor with a null AppwriteAuthService (no Appwrite integration in tests).
    AuthController(AuthService service, DemoAuthorizationService authorizationService) {
        this(service, authorizationService, null);
    }

    /**
     * GET /api/auth/user — look up a single account record by email.
     *
     * @param email        optional query param: the email whose account is requested
     * @param callerHeader caller identity from the {@code X-Demo-User-Email} header
     * @return 200 OK with the {@link AppUser} JSON if found; 200 OK with the JSON literal {@code null}
     *         if no such user exists; 403 FORBIDDEN (body {@code null}) if the caller is not allowed.
     * Authorization: a non-staff/admin caller may only look up THEIR OWN email (caller must resolve to a
     * verified email equal to the requested one); staff/admins may look up any email. This prevents
     * account enumeration and PII disclosure.
     */
    @GetMapping("/user")
    public ResponseEntity<?> user(
            @RequestParam(required = false) String email,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String callerHeader) {
        // An account record (with role/PII) may only be looked up by the user
        // themselves or by staff/admin — prevents account enumeration / PII disclosure.
        // Normalize the requested email for a case-insensitive self-match check.
        String requested = email == null ? "" : email.trim().toLowerCase();
        // Non-privileged callers are restricted to their own record.
        if (!authorizationService.isStaffOrAdmin(callerHeader)) {
            // Resolve the caller's verified email; must be present and equal to the requested email.
            String caller = authorizationService.resolveEmail(callerHeader);
            if (caller == null || caller.isBlank() || !caller.equalsIgnoreCase(requested)) {
                // Not self and not staff/admin → forbidden (returns the JSON literal null in the body).
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON).body("null");
            }
        }
        // Authorized: return the user if found, otherwise a JSON null with 200 OK.
        return service.findByEmail(email)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null"));
    }

    /**
     * Resolves the current caller from a verified Appwrite session (or the demo
     * fallback header when enabled) and returns the backend user record. This is
     * the source of truth for identity and role; the frontend never asserts them.
     */
    /**
     * GET /api/auth/me — return the currently authenticated user's backend record.
     *
     * @param demoUserEmail caller identity from the {@code X-Demo-User-Email} header (or the verified
     *                      Appwrite session); used by {@link DemoAuthorizationService#currentUser} to resolve identity
     * @return 200 OK with the {@link AppUser} JSON when the caller resolves to a user; 200 OK with the
     *         JSON literal {@code null} when no user can be resolved (i.e. not signed in).
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "X-Demo-User-Email", required = false) String demoUserEmail) {
        // Resolve the caller to a backend user record (source of truth for identity/role).
        AppUser user = authorizationService.currentUser(demoUserEmail);
        if (user == null) {
            // Not signed in / unresolvable → JSON null with 200 OK (not an error).
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null");
        }
        return ResponseEntity.ok(user);
    }

    /**
     * POST /api/auth/signin — sign in with credentials.
     *
     * @param request validated ({@code @Valid}) request body deserialized into {@link SignInRequest}
     *                (invalid bodies trigger a 400 before this method runs)
     * @return a {@link SignInResponse} built from the authenticated user; 200 OK.
     * Errors/edge cases for bad credentials are raised by {@link AuthService#signIn}.
     */
    @PostMapping("/signin")
    public SignInResponse signIn(@Valid @RequestBody SignInRequest request) {
        return SignInResponse.from(service.signIn(request));
    }

    /**
     * POST /api/auth/signup — register a new account.
     *
     * @param body request body; reads {@code email} (required), {@code fullName} (or legacy
     *             {@code full_name}; defaults to the email if blank), and {@code role}
     *             (defaults to "student"; only "student" or "staff" allowed)
     * @return a {@link SignInResponse} for the newly created user.
     * Status: 201 CREATED (via {@code @ResponseStatus}).
     * Errors: {@link BadRequestException} if email is blank, if role is "admin" (cannot self-assign),
     * or if role is not "student"/"staff"; {@link ConflictException} if a user with that email exists.
     */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED) // return 201 Created instead of the default 200 on success
    public SignInResponse signUp(@RequestBody Map<String, Object> body) {
        // Normalize email (trim + lowercase) for consistent storage and uniqueness checks.
        String email = body.get("email") != null ? String.valueOf(body.get("email")).trim().toLowerCase() : "";
        // Accept either camelCase "fullName" or legacy snake_case "full_name".
        String fullName = body.get("fullName") != null ? String.valueOf(body.get("fullName")).trim()
                : (body.get("full_name") != null ? String.valueOf(body.get("full_name")).trim() : "");
        // Default to "student" when no role is supplied.
        String role = body.get("role") != null ? String.valueOf(body.get("role")).trim().toLowerCase() : "student";

        // Email is mandatory.
        if (email.isBlank()) {
            throw new BadRequestException("Email is required.");
        }
        // Privilege-escalation guard: admin cannot be granted via self-signup.
        if ("admin".equalsIgnoreCase(role)) {
            throw new BadRequestException("Admin role cannot be self-assigned.");
        }
        // Only the two self-serviceable roles are permitted.
        if (!"student".equalsIgnoreCase(role) && !"staff".equalsIgnoreCase(role)) {
            throw new BadRequestException("Role must be 'student' or 'staff'.");
        }
        // Reject duplicate registrations.
        if (service.findByEmail(email).isPresent()) {
            throw new ConflictException("A user with this email already exists.");
        }
        // Create the account, defaulting the display name to the email when no name was given.
        AppUser user = service.signUp(email, fullName.isBlank() ? email : fullName, role);
        return SignInResponse.from(user);
    }

    /**
     * POST /api/auth/signout — sign out.
     *
     * @return {@code {"success": true}}; 200 OK. No-op server-side (see note below); the client is
     *         responsible for discarding its own token/session.
     */
    @PostMapping("/signout")
    public Map<String, Object> signOut() {
        // Backend is stateless — no server-side session to invalidate.
        // The client should discard its token/session on receipt.
        return Map.of("success", true);
    }

    /**
     * POST /api/auth/forgot-password — initiate a password reset.
     *
     * @param body request body with a required {@code email} field
     * @return {@code {"sent": true}} (with an extra {@code message} in demo mode); 200 OK.
     * Privacy: always reports success regardless of whether the account exists, to avoid leaking
     * account existence. A real recovery email is only triggered when the user exists AND Appwrite
     * is configured; otherwise it is a demo no-op.
     * Errors: {@link BadRequestException} if {@code email} is blank.
     */
    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@RequestBody Map<String, Object> body) {
        // Normalize the email.
        String email = body.get("email") != null ? String.valueOf(body.get("email")).trim().toLowerCase() : "";
        if (email.isBlank()) {
            throw new BadRequestException("Email is required.");
        }
        // Always return success to avoid leaking whether an account exists
        boolean userExists = service.findByEmail(email).isPresent();
        // Only fire a real recovery email when the account exists and Appwrite is wired up.
        if (userExists && appwriteAuthService != null && appwriteAuthService.isConfigured()) {
            // Appwrite handles password reset emails; trigger via SDK if configured
            appwriteAuthService.triggerPasswordRecovery(email);
            return Map.of("sent", true);
        }
        // Demo fallback: claim success but send nothing.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", true);
        result.put("message", "demo mode — no email sent");
        return result;
    }
}
