package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoundItemRepository extends JpaRepository<FoundItem, String> {
}
