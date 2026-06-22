package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.CustodyEvent;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CustodyEventRepository extends MongoRepository<CustodyEvent, String> {
    List<CustodyEvent> findByFoundItemIdOrderBySequenceNumberAsc(String foundItemId);
}
