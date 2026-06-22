package com.FBLA.WebCodingDev26Backend.dto;

public record AssetLookupResponse(
        boolean recognized,
        String assetTag,
        String assetType,
        String message
) {
}
