package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.HealthResponse;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final MongoTemplate mongoTemplate;

    public HealthService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public HealthResponse health() {
        return new HealthResponse("ok", "mongodb", isConnected());
    }

    private boolean isConnected() {
        try {
            Document result = mongoTemplate.executeCommand(new Document("ping", 1));
            Object ok = result.get("ok");
            return ok instanceof Number number && number.doubleValue() == 1.0;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
