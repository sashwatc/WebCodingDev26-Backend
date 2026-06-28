package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.Claim;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link Claim} entity (ownership claims on found items).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations;
 * only the custom queries below are declared.
 */
public interface ClaimRepository extends MongoRepository<Claim, String> {
    // Derived query: returns all claims whose "claimantEmail" equals the argument
    // (every claim filed by that person), in unspecified order.
    List<Claim> findByClaimantEmail(String claimantEmail);

    // Derived query: returns all claims whose "foundItemId" equals the argument
    // (every claim against that one found item), in unspecified order.
    List<Claim> findByFoundItemId(String foundItemId);
}
