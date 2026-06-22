package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.AssetLookupResponse;
import com.FBLA.WebCodingDev26Backend.service.AssetRegistryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssetController {
    private final AssetRegistryService assetRegistryService;

    public AssetController(AssetRegistryService assetRegistryService) {
        this.assetRegistryService = assetRegistryService;
    }

    @GetMapping("/api/assets/lookup")
    public AssetLookupResponse lookup(@RequestParam("tag") String tag) {
        return assetRegistryService.lookup(tag);
    }
}
