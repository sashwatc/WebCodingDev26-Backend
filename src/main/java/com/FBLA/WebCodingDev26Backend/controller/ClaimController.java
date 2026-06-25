package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {
    private final ClaimRepository claims;
    private final DemoAuthorizationService authorizationService;

    public ClaimController(ClaimRepository claims, DemoAuthorizationService authorizationService) {
        this.claims = claims;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/mine")
    public List<Claim> mine(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required to view claims.");
        }
        return claims.findByClaimantEmail(verifiedEmail);
    }
}
