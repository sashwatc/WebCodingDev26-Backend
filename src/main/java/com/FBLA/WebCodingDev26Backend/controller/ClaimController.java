package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {
    private final ClaimRepository claims;

    public ClaimController(ClaimRepository claims) {
        this.claims = claims;
    }

    @GetMapping("/mine")
    public List<Claim> mine(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return List.of();
        }
        return claims.findByClaimantEmail(userEmail.trim().toLowerCase(Locale.ROOT));
    }
}
