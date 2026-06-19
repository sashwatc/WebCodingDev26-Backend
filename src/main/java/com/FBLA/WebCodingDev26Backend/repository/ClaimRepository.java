package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.Claim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, String> {
}
