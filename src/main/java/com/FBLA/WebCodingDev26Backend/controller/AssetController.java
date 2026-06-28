package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.AssetLookupResponse;
import com.FBLA.WebCodingDev26Backend.service.AssetRegistryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asset-registry lookup endpoint.
 *
 * <p>Exposes {@code GET /api/assets/lookup}, which resolves a physical asset tag
 * (e.g. a school-property barcode/QR identifier) to its registry record. The work
 * is delegated to {@link AssetRegistryService}. No authorization is enforced here.</p>
 */
@RestController // REST controller: handler return value is serialized to the response body
public class AssetController {
    // Looks up asset records by tag in the asset registry.
    private final AssetRegistryService assetRegistryService;

    /** Constructor injection of the asset registry service. */
    public AssetController(AssetRegistryService assetRegistryService) {
        this.assetRegistryService = assetRegistryService;
    }

    /**
     * GET /api/assets/lookup — resolve an asset tag to its registry record.
     *
     * @param tag required query parameter {@code ?tag=...}: the asset tag to look up
     * @return the {@link AssetLookupResponse} for the tag (per {@link AssetRegistryService#lookup}); 200 OK.
     *         No authorization required. Behavior for an unknown tag is defined by the service.
     */
    @GetMapping("/api/assets/lookup")
    public AssetLookupResponse lookup(@RequestParam("tag") String tag) {
        return assetRegistryService.lookup(tag);
    }
}
