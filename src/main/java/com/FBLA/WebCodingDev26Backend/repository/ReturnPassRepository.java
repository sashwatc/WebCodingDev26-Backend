package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.ReturnPass;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link ReturnPass} entity
 * (passes authorizing pickup/return of a claimed item). Extending {@link MongoRepository}
 * auto-generates the standard CRUD operations; only the custom queries below are declared.
 */
public interface ReturnPassRepository extends MongoRepository<ReturnPass, String> {
    // Derived query: returns all return passes whose "claimId" equals the argument
    // (all passes issued for one claim), in unspecified order.
    List<ReturnPass> findByClaimId(String claimId);

    // Derived query: finds the single return pass whose "oneTimeCode" equals the argument
    // (used to redeem a pass by its code). Returns Optional.empty() if no pass has that code.
    Optional<ReturnPass> findByOneTimeCode(String oneTimeCode);
}
