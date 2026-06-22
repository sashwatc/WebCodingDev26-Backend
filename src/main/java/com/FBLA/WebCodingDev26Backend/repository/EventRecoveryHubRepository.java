package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.EventRecoveryHub;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventRecoveryHubRepository extends MongoRepository<EventRecoveryHub, String> {
    List<EventRecoveryHub> findByPublicEnabledTrue();
}
