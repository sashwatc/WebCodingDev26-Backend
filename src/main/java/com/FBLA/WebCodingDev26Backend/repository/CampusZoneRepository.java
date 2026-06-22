package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.CampusZone;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CampusZoneRepository extends MongoRepository<CampusZone, String> {
    Optional<CampusZone> findByLabel(String label);
}
