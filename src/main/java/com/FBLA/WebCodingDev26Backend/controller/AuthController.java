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

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final DemoAuthorizationService authorizationService;
    private final AppwriteAuthService appwriteAuthService;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthController(AuthService service, DemoAuthorizationService authorizationService, AppwriteAuthService appwriteAuthService) {
        this.service = service;
        this.authorizationService = authorizationService;
        this.appwriteAuthService = appwriteAuthService;
    }

    // Package-private constructor for test compatibility
    AuthController(AuthService service, DemoAuthorizationService authorizationService) {
        this(service, authorizationService, null);
    }

    @GetMapping("/user")
    public ResponseEntity<?> user(@RequestParam(required = false) String email) {
        return service.findByEmail(email)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null"));
    }

    /**
     * Resolves the current caller from a verified Appwrite session (or the demo
     * fallback header when enabled) and returns the backend user record. This is
     * the source of truth for identity and role; the frontend never asserts them.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "X-Demo-User-Email", required = false) String demoUserEmail) {
        AppUser user = authorizationService.currentUser(demoUserEmail);
        if (user == null) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null");
        }
        return ResponseEntity.ok(user);
    }

    @PostMapping("/signin")
    public SignInResponse signIn(@Valid @RequestBody SignInRequest request) {
        return SignInResponse.from(service.signIn(request));
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignInResponse signUp(@RequestBody Map<String, Object> body) {
        String email = body.get("email") != null ? String.valueOf(body.get("email")).trim().toLowerCase() : "";
        String fullName = body.get("fullName") != null ? String.valueOf(body.get("fullName")).trim()
                : (body.get("full_name") != null ? String.valueOf(body.get("full_name")).trim() : "");
        String role = body.get("role") != null ? String.valueOf(body.get("role")).trim().toLowerCase() : "student";

        if (email.isBlank()) {
            throw new BadRequestException("Email is required.");
        }
        if ("admin".equalsIgnoreCase(role)) {
            throw new BadRequestException("Admin role cannot be self-assigned.");
        }
        if (!"student".equalsIgnoreCase(role) && !"staff".equalsIgnoreCase(role)) {
            throw new BadRequestException("Role must be 'student' or 'staff'.");
        }
        if (service.findByEmail(email).isPresent()) {
            throw new ConflictException("A user with this email already exists.");
        }
        AppUser user = service.signUp(email, fullName.isBlank() ? email : fullName, role);
        return SignInResponse.from(user);
    }

    @PostMapping("/signout")
    public Map<String, Object> signOut() {
        // Backend is stateless — no server-side session to invalidate.
        // The client should discard its token/session on receipt.
        return Map.of("success", true);
    }

    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@RequestBody Map<String, Object> body) {
        String email = body.get("email") != null ? String.valueOf(body.get("email")).trim().toLowerCase() : "";
        if (email.isBlank()) {
            throw new BadRequestException("Email is required.");
        }
        // Always return success to avoid leaking whether an account exists
        boolean userExists = service.findByEmail(email).isPresent();
        if (userExists && appwriteAuthService != null && appwriteAuthService.isConfigured()) {
            // Appwrite handles password reset emails; trigger via SDK if configured
            appwriteAuthService.triggerPasswordRecovery(email);
            return Map.of("sent", true);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", true);
        result.put("message", "demo mode — no email sent");
        return result;
    }
}
