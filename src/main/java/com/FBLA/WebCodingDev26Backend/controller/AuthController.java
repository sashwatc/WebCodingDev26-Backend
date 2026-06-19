package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.SignInRequest;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @GetMapping("/user")
    public ResponseEntity<?> user(@RequestParam(required = false) String email) {
        return service.findByEmail(email)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null"));
    }

    @PostMapping("/signin")
    public AppUser signIn(@Valid @RequestBody SignInRequest request) {
        return service.signIn(request);
    }
}
