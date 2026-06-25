package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.SignInRequest;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AppUserRepository repository;
    private final ClockService clock;
    private final String adminEmail;

    public AuthService(AppUserRepository repository, ClockService clock, @Value("${app.admin-email:}") String adminEmail) {
        this.repository = repository;
        this.clock = clock;
        this.adminEmail = normalize(adminEmail);
    }

    public Optional<AppUser> findByEmail(String email) {
        String normalizedEmail = normalize(email);
        return normalizedEmail.isBlank() ? Optional.empty() : repository.findByEmail(normalizedEmail);
    }

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
        applyNotificationDefaults(user);
        user.setUpdatedDate(now);
        return repository.save(user);
    }

    /**
     * Upserts a backend user from a server-verified identity (for example, an
     * Appwrite account whose JWT was validated by {@code AppwriteAuthService}).
     * Passwords are never received or stored here; identity and the admin
     * decision are determined upstream and only mapped onto the backend record.
     */
    public AppUser upsertFromVerifiedIdentity(String appwriteUserId, String email, String fullName, boolean admin) {
        String normalizedEmail = normalize(email);
        String now = clock.now();
        AppUser user = repository.findByEmail(normalizedEmail).orElseGet(() -> {
            AppUser created = new AppUser();
            created.setId("user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            created.setCreatedDate(now);
            created.setAvatarUrl("");
            return created;
        });
        if (appwriteUserId != null && !appwriteUserId.isBlank()) {
            user.setAppwriteUserId(appwriteUserId.trim());
        }
        String resolvedName = fullName == null ? "" : fullName.trim();
        if (resolvedName.isBlank()) {
            resolvedName = user.getFullName() == null || user.getFullName().isBlank()
                    ? normalizedEmail
                    : user.getFullName();
        }
        user.setFullName(resolvedName);
        user.setEmail(normalizedEmail);
        user.setRole(admin ? "admin" : "student");
        applyNotificationDefaults(user);
        user.setUpdatedDate(now);
        return repository.save(user);
    }

    private void applyNotificationDefaults(AppUser user) {
        if (user.getEmailNotificationsEnabled() == null) {
            user.setEmailNotificationsEnabled(true);
        }
        if (user.getSmsOptIn() == null) {
            user.setSmsOptIn(false);
        }
        if (user.getSmsNotificationsEnabled() == null) {
            user.setSmsNotificationsEnabled(false);
        }
        if (user.getWebhookNotificationsEnabled() == null) {
            user.setWebhookNotificationsEnabled(true);
        }
        if (user.getNotificationCategories() == null) {
            user.setNotificationCategories(new ArrayList<>(List.of("all")));
        }
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
