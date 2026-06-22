package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.ReturnPass;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReturnPassRepository extends MongoRepository<ReturnPass, String> {
    List<ReturnPass> findByClaimId(String claimId);
    Optional<ReturnPass> findByOneTimeCode(String oneTimeCode);
}
