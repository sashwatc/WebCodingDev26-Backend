package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.AssetLookupResponse;
import com.FBLA.WebCodingDev26Backend.model.AssetRegistryRecord;
import com.FBLA.WebCodingDev26Backend.repository.AssetRegistryRecordRepository;
import org.springframework.stereotype.Service;

@Service
public class AssetRegistryService {
    private final AssetRegistryRecordRepository records;

    public AssetRegistryService(AssetRegistryRecordRepository records) {
        this.records = records;
    }

    public AssetLookupResponse lookup(String tag) {
        return records.findByAssetTagIgnoreCase(tag == null ? "" : tag.trim())
                .map(this::recognized)
                .orElseGet(() -> new AssetLookupResponse(false, tag, "", "Asset tag was not found in the seeded demo adapter."));
    }

    private AssetLookupResponse recognized(AssetRegistryRecord record) {
        return new AssetLookupResponse(
                true,
                record.getAssetTag(),
                record.getAssetType(),
                "Recognized school-owned property. It will be routed to the appropriate department."
        );
    }
}
