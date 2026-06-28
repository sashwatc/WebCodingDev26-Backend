package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.AppUser;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link AppUser} entity (user accounts).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations
 * (save, findById, findAll, delete, count, etc.) — no implementation is written by hand.
 */
public interface AppUserRepository extends MongoRepository<AppUser, String> {
    // Derived query: looks up the single user whose "email" field equals the argument.
    // Returns Optional.empty() if no user has that email.
    Optional<AppUser> findByEmail(String email);
}
