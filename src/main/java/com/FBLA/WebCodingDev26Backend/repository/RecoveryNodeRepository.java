package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.RecoveryNode;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link RecoveryNode} entity (recovery network nodes/locations).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations
 * (save, findById, findAll, delete, count, etc.). No custom query methods are declared,
 * so the inherited CRUD API is all that is exposed.
 */
public interface RecoveryNodeRepository extends MongoRepository<RecoveryNode, String> {
}
