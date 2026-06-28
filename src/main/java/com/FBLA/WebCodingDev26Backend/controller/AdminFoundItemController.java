package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.FoundItemService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin/staff view of the found-item inventory.
 *
 * <p>Serves the base route {@code /api/admin/items}. Unlike the public found-item
 * endpoints, this exposes the full moderation list (including pending/unpublished
 * items) and is therefore gated to staff/admin callers. Identity comes from the
 * {@code X-Demo-User-Email} header verified by {@link DemoAuthorizationService};
 * the listing itself is delegated to {@link FoundItemService}.</p>
 */
@RestController // REST controller: handler return values are serialized to the response body
@RequestMapping("/api/admin/items") // base route for this controller
public class AdminFoundItemController {
    // Found-item business logic, including the admin (full/pending) listing.
    private final FoundItemService service;
    // Resolves the caller and enforces staff/admin authorization.
    private final DemoAuthorizationService authorizationService;

    /** Constructor injection of the found-item service and authorization service. */
    public AdminFoundItemController(FoundItemService service, DemoAuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    /**
     * GET /api/admin/items — list the full found-item inventory for moderation.
     *
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to staff/admin
     * @return every found item including pending/unpublished ones, via {@link FoundItemService#listAdmin()}; 200 OK
     * Authorization: staff/admin required. Throws via the authorization service if the caller is not staff/admin.
     */
    @GetMapping // HTTP GET on the base route /api/admin/items
    public List<FoundItem> list(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Staff moderate the found-item queue alongside admins, so they need the
        // full (including pending) inventory list, not just the public view.
        authorizationService.requireStaffOrAdmin(userEmail);
        return service.listAdmin();
    }
}
