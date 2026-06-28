package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.HealthResponse;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Reports backend liveness/readiness, including MongoDB connectivity.
 *
 * <p>Used by the health endpoint to confirm the service is up and that its primary
 * datastore is reachable.
 *
 * <p>Collaborators: {@link MongoTemplate} (issues a low-level ping command).
 */
@Service
public class HealthService {
    /** Spring Data handle used to issue raw MongoDB commands for the connectivity probe. */
    private final MongoTemplate mongoTemplate;

    /**
     * @param mongoTemplate template used to ping the database
     */
    public HealthService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Builds the health snapshot returned to callers.
     *
     * @return a {@link HealthResponse} with overall status {@code "ok"}, dependency
     *         name {@code "mongodb"}, and a boolean indicating whether Mongo responded
     */
    public HealthResponse health() {
        return new HealthResponse("ok", "mongodb", isConnected());
    }

    /**
     * Probes MongoDB by running the {@code {ping: 1}} admin command.
     *
     * @return {@code true} when Mongo replies with {@code ok == 1.0}; {@code false} if
     *         the command throws (e.g. the database is unreachable) or returns a
     *         non-successful result. Never propagates exceptions.
     */
    private boolean isConnected() {
        try {
            // Issue the lightweight server ping command.
            Document result = mongoTemplate.executeCommand(new Document("ping", 1));
            // Mongo signals success via an "ok" field equal to 1.
            Object ok = result.get("ok");
            return ok instanceof Number number && number.doubleValue() == 1.0;
        } catch (RuntimeException exception) {
            // Any failure to reach/execute means "not connected".
            return false;
        }
    }
}
