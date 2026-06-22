package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.PreventionAlert;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PreventionAlertRepository extends MongoRepository<PreventionAlert, String> {
}
