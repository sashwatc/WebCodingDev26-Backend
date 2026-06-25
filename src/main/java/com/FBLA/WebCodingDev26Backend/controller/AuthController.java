package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.SignInRequest;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.service.AuthService;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final DemoAuthorizationService authorizationService;

    public AuthController(AuthService service, DemoAuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
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
    public AppUser signIn(@Valid @RequestBody SignInRequest request) {
        return service.signIn(request);
    }
}
