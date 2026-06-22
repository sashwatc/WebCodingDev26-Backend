package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.RecoveryNode;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RecoveryNodeRepository extends MongoRepository<RecoveryNode, String> {
}
