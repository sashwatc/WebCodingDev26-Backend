package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findByEmail(String email);
}
