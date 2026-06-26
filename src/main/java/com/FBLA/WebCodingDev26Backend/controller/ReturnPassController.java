package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRedeemRequest;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRequest;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassResponse;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassVerifyRequest;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassVerifyResponse;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.ReturnPass;
import com.FBLA.WebCodingDev26Backend.repository.ReturnPassRepository;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.ReturnPassService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReturnPassController {
    private final ReturnPassService returnPassService;
    private final ReturnPassRepository returnPassRepository;
    private final DemoAuthorizationService authorizationService;

    public ReturnPassController(ReturnPassService returnPassService, ReturnPassRepository returnPassRepository, DemoAuthorizationService authorizationService) {
        this.returnPassService = returnPassService;
        this.returnPassRepository = returnPassRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/api/claims/{claimId}/return-pass")
    public ReturnPassResponse create(
            @PathVariable String claimId,
            @RequestBody ReturnPassRequest request,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return returnPassService.create(claimId, request, admin);
    }

    @GetMapping("/api/return-passes/{idOrClaimId}")
    public ReturnPassResponse get(@PathVariable String idOrClaimId, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Try by pass ID first, then by claimId
        if (returnPassRepository.existsById(idOrClaimId)) {
            return returnPassService.get(idOrClaimId, userEmail, authorizationService);
        }
        List<ReturnPass> byClaimId = returnPassRepository.findByClaimId(idOrClaimId);
        if (!byClaimId.isEmpty()) {
            String passId = byClaimId.get(0).getId();
            return returnPassService.get(passId, userEmail, authorizationService);
        }
        throw new NotFoundException("Return Pass not found");
    }

    @PostMapping("/api/return-passes/verify")
    public ReturnPassVerifyResponse verify(@Valid @RequestBody ReturnPassVerifyRequest request) {
        return returnPassService.verify(request);
    }

    @PostMapping("/api/return-passes/redeem")
    public ReturnPassResponse redeemByCode(
            @RequestBody ReturnPassRedeemRequest request,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return returnPassService.redeemByCode(request, admin);
    }

    @PostMapping("/api/return-passes/{id}/redeem")
    public ReturnPassResponse redeem(
            @PathVariable String id,
            @Valid @RequestBody ReturnPassRedeemRequest request,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return returnPassService.redeem(id, request, admin);
    }

    @PostMapping("/api/return-passes/{id}/reminder")
    public ReturnPassResponse sendPickupReminder(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return returnPassService.sendPickupReminder(id, admin);
    }
}
