package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.LostReport;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link LostReport} entity (reports of lost items).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations;
 * only the custom query below is declared.
 */
public interface LostReportRepository extends MongoRepository<LostReport, String> {
    // Derived query: returns all lost reports whose "contactEmail" equals the argument
    // (every report filed by that contact), in unspecified order.
    List<LostReport> findByContactEmail(String contactEmail);
}
