package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.AssetRegistryRecord;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link AssetRegistryRecord} entity
 * (pre-registered/tagged assets). Extending {@link MongoRepository} auto-generates
 * the standard CRUD operations; only the custom lookup below is declared.
 */
public interface AssetRegistryRecordRepository extends MongoRepository<AssetRegistryRecord, String> {
    // Derived query: finds the single record whose "assetTag" equals the argument,
    // comparing case-insensitively (IgnoreCase). Returns Optional.empty() if none match.
    Optional<AssetRegistryRecord> findByAssetTagIgnoreCase(String assetTag);
}
