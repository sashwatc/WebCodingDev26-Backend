package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.Claim;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClaimRepository extends MongoRepository<Claim, String> {
    List<Claim> findByClaimantEmail(String claimantEmail);
    List<Claim> findByFoundItemId(String foundItemId);
}
