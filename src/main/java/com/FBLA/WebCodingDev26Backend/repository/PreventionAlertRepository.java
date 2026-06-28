package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.PreventionAlert;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link PreventionAlert} entity (loss-prevention alerts).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations
 * (save, findById, findAll, delete, count, etc.). No custom query methods are declared,
 * so the inherited CRUD API is all that is exposed.
 */
public interface PreventionAlertRepository extends MongoRepository<PreventionAlert, String> {
}
