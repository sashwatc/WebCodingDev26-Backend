package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.RecoveryMission;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RecoveryMissionRepository extends MongoRepository<RecoveryMission, String> {
    List<RecoveryMission> findByRecoveryCaseId(String recoveryCaseId);
}
