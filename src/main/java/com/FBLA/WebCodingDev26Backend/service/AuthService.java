package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.SignInRequest;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AppUserRepository repository;
    private final ClockService clock;
    private final String adminEmail;

    public AuthService(AppUserRepository repository, ClockService clock, @Value("${app.admin-email}") String adminEmail) {
        this.repository = repository;
        this.clock = clock;
        this.adminEmail = normalize(adminEmail);
    }

    public Optional<AppUser> findByEmail(String email) {
        String normalizedEmail = normalize(email);
        return normalizedEmail.isBlank() ? Optional.empty() : repository.findByEmail(normalizedEmail);
    }

    @Transactional
    public AppUser signIn(SignInRequest request) {
        String normalizedEmail = normalize(request.getEmail());
        String now = clock.now();
        AppUser user = repository.findByEmail(normalizedEmail).orElseGet(() -> {
            AppUser created = new AppUser();
            created.setId("user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            created.setCreatedDate(now);
            created.setAvatarUrl("");
            return created;
        });
        user.setFullName(request.getFullName().trim());
        user.setEmail(normalizedEmail);
        user.setRole(normalizedEmail.equals(adminEmail) ? "admin" : "student");
        user.setUpdatedDate(now);
        return repository.save(user);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
