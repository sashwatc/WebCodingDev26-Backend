package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.AssetRegistryRecord;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AssetRegistryRecordRepository extends MongoRepository<AssetRegistryRecord, String> {
    Optional<AssetRegistryRecord> findByAssetTagIgnoreCase(String assetTag);
}
