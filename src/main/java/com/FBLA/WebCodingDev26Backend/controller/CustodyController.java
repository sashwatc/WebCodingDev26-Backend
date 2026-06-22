package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.CustodyVerificationResponse;
import com.FBLA.WebCodingDev26Backend.dto.MoveItemRequest;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.CustodyEvent;
import com.FBLA.WebCodingDev26Backend.service.CustodyLedgerService;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/custody/items")
public class CustodyController {
    private final CustodyLedgerService custodyLedgerService;
    private final DemoAuthorizationService authorizationService;

    public CustodyController(CustodyLedgerService custodyLedgerService, DemoAuthorizationService authorizationService) {
        this.custodyLedgerService = custodyLedgerService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{foundItemId}")
    public List<?> list(@PathVariable String foundItemId, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        List<CustodyEvent> events = custodyLedgerService.list(foundItemId);
        if (authorizationService.isAdmin(userEmail)) {
            return events;
        }
        return events.stream().map(custodyLedgerService::toPublicEvent).toList();
    }

    @GetMapping("/{foundItemId}/verify")
    public CustodyVerificationResponse verify(@PathVariable String foundItemId) {
        return custodyLedgerService.verify(foundItemId);
    }

    @PostMapping("/{foundItemId}/move")
    public CustodyEvent move(
            @PathVariable String foundItemId,
            @Valid @RequestBody MoveItemRequest request,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return custodyLedgerService.move(foundItemId, request, admin);
    }
}
