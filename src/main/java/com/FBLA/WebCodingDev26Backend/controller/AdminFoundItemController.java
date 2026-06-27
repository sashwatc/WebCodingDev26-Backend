package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.FoundItemService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/items")
public class AdminFoundItemController {
    private final FoundItemService service;
    private final DemoAuthorizationService authorizationService;

    public AdminFoundItemController(FoundItemService service, DemoAuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public List<FoundItem> list(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Staff moderate the found-item queue alongside admins, so they need the
        // full (including pending) inventory list, not just the public view.
        authorizationService.requireStaffOrAdmin(userEmail);
        return service.listAdmin();
    }
}
