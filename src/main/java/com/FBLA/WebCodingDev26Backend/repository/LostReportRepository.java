package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.LostReport;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LostReportRepository extends MongoRepository<LostReport, String> {
    List<LostReport> findByContactEmail(String contactEmail);
}
