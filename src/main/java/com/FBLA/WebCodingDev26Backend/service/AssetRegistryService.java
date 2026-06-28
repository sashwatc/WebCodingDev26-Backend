package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.AssetLookupResponse;
import com.FBLA.WebCodingDev26Backend.model.AssetRegistryRecord;
import com.FBLA.WebCodingDev26Backend.repository.AssetRegistryRecordRepository;
import org.springframework.stereotype.Service;

/**
 * Looks up school-owned asset tags against the seeded asset registry to tell whether
 * a found item is recognized institutional property (and so should be routed to the
 * owning department) rather than a personal lost-and-found item.
 *
 * <p>Collaborates with {@link AssetRegistryRecordRepository} for the (demo-seeded)
 * tag-to-asset mapping. Read-only; no persistence writes.
 */
@Service
public class AssetRegistryService {
    /** Repository of seeded asset-tag records used to resolve a scanned/entered tag. */
    private final AssetRegistryRecordRepository records;

    /** Injects the asset-registry repository. */
    public AssetRegistryService(AssetRegistryRecordRepository records) {
        this.records = records;
    }

    /**
     * Resolves an asset tag to a lookup response.
     *
     * @param tag the asset tag to look up (null treated as empty; trimmed; matched case-insensitively)
     * @return a recognized response (with asset type and routing note) when the tag exists,
     *         otherwise an unrecognized response echoing the tag with a "not found" message
     */
    public AssetLookupResponse lookup(String tag) {
        return records.findByAssetTagIgnoreCase(tag == null ? "" : tag.trim())
                .map(this::recognized)
                .orElseGet(() -> new AssetLookupResponse(false, tag, "", "Asset tag was not found in the seeded demo adapter."));
    }

    /** Maps a found registry record into a "recognized" response carrying its tag, type, and routing note. */
    private AssetLookupResponse recognized(AssetRegistryRecord record) {
        return new AssetLookupResponse(
                true,
                record.getAssetTag(),
                record.getAssetType(),
                "Recognized school-owned property. It will be routed to the appropriate department."
        );
    }
}
