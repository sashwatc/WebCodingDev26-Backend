package com.FBLA.WebCodingDev26Backend.dto;

/**
 * Response payload for an asset-tag lookup. Returned (direction: server -> client)
 * when a found item's scanned/entered asset tag is checked against the institution's
 * asset registry, reporting whether the tag corresponds to a known, tracked asset.
 */
public record AssetLookupResponse(
        // True when the supplied asset tag was matched to a recognized asset record;
        // false when no matching asset could be found.
        boolean recognized,
        // The asset tag that was looked up (e.g. an inventory/barcode identifier);
        // typically echoes the queried tag.
        String assetTag,
        // Human-readable category/type of the matched asset (e.g. "Laptop", "Chromebook");
        // may be null/blank when not recognized.
        String assetType,
        // Human-readable status or explanation describing the lookup outcome.
        String message
) {
}
