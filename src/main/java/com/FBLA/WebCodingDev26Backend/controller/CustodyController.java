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

/**
 * Custody-ledger endpoints for a found item (chain-of-custody tracking).
 *
 * <p>Serves the base route {@code /api/custody/items}. Provides the custody event
 * history for an item, a tamper/integrity verification of that history, and an
 * admin-only action to record a custody move (handoff/relocation). Work is delegated
 * to {@link CustodyLedgerService}; identity/role comes from the {@code X-Demo-User-Email}
 * header via {@link DemoAuthorizationService}.</p>
 *
 * <p>Visibility note: full custody events may include sensitive details, so the list
 * endpoint returns full events only to admins and a redacted ("public") projection to
 * everyone else.</p>
 */
@RestController // REST controller: handler return values are serialized to the response body
@RequestMapping("/api/custody/items") // base route for all custody endpoints
public class CustodyController {
    // Builds, verifies, and appends to the chain-of-custody ledger for found items.
    private final CustodyLedgerService custodyLedgerService;
    // Resolves the caller and enforces admin authorization on the move endpoint.
    private final DemoAuthorizationService authorizationService;

    /** Constructor injection of the custody ledger service and authorization service. */
    public CustodyController(CustodyLedgerService custodyLedgerService, DemoAuthorizationService authorizationService) {
        this.custodyLedgerService = custodyLedgerService;
        this.authorizationService = authorizationService;
    }

    /**
     * GET /api/custody/items/{foundItemId} — list the custody history for an item.
     *
     * @param foundItemId path variable: the found item's id
     * @param userEmail   caller identity from the {@code X-Demo-User-Email} header
     * @return the list of {@link CustodyEvent}s; 200 OK. Admins receive full events; all other callers
     *         receive a redacted "public" projection of each event. No sign-in is required to read.
     */
    @GetMapping("/{foundItemId}")
    public List<?> list(@PathVariable String foundItemId, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Fetch the full ledger for the item.
        List<CustodyEvent> events = custodyLedgerService.list(foundItemId);
        // Admins see everything.
        if (authorizationService.isAdmin(userEmail)) {
            return events;
        }
        // Non-admins get a redacted projection of each event.
        return events.stream().map(custodyLedgerService::toPublicEvent).toList();
    }

    /**
     * GET /api/custody/items/{foundItemId}/verify — verify the integrity of the custody chain.
     *
     * @param foundItemId path variable: the found item's id
     * @return a {@link CustodyVerificationResponse} describing whether the ledger is intact/unbroken;
     *         200 OK. No authorization required.
     */
    @GetMapping("/{foundItemId}/verify")
    public CustodyVerificationResponse verify(@PathVariable String foundItemId) {
        return custodyLedgerService.verify(foundItemId);
    }

    /**
     * POST /api/custody/items/{foundItemId}/move — record a custody move (handoff/relocation).
     *
     * @param foundItemId path variable: the found item's id
     * @param request     validated ({@code @Valid}) request body {@link MoveItemRequest} describing the
     *                    move (e.g. new location/holder); invalid bodies trigger a 400 before this runs
     * @param userEmail   caller identity from the {@code X-Demo-User-Email} header; must resolve to a full admin
     * @return the newly appended {@link CustodyEvent}; 200 OK.
     * Authorization: ADMIN required (the admin is recorded as the actor of the move).
     */
    @PostMapping("/{foundItemId}/move")
    public CustodyEvent move(
            @PathVariable String foundItemId,
            @Valid @RequestBody MoveItemRequest request,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Authorization gate: admin only; capture the admin to attribute the move.
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return custodyLedgerService.move(foundItemId, request, admin);
    }
}
