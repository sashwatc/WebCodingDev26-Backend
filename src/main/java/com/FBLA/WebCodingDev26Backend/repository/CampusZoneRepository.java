package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.CampusZone;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link CampusZone} entity (named campus locations).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations;
 * only the custom lookup below is declared.
 */
public interface CampusZoneRepository extends MongoRepository<CampusZone, String> {
    // Derived query: finds the single zone whose "label" field equals the argument.
    // Returns Optional.empty() if no zone has that label.
    Optional<CampusZone> findByLabel(String label);
}
