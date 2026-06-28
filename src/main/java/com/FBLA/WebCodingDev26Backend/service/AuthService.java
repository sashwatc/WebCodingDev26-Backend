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

/**
 * Owns the backend's {@link AppUser} identity store: looking up users by email and
 * upserting user records on sign-in, sign-up, or from an externally verified identity.
 *
 * <p>Business rules this service enforces:
 * <ul>
 *   <li>Emails are always normalized (trimmed, lowercased) before lookup/storage.</li>
 *   <li>The single configured {@code app.admin-email} is auto-granted the {@code admin} role;
 *       otherwise a sensible default role is applied without overwriting existing roles.</li>
 *   <li>Notification preferences are defaulted on every write so records stay complete.</li>
 * </ul>
 * Note: this service never receives or stores passwords — credentials are handled
 * upstream (e.g. Appwrite). Collaborators: {@link AppUserRepository} (persistence) and
 * {@link ClockService} (timestamps).
 */
@Service
public class AuthService {
    /** User persistence: find-by-email lookups and upserts. */
    private final AppUserRepository repository;
    /** Supplies ISO timestamps for created/updated dates. */
    private final ClockService clock;
    /** Normalized email of the single privileged admin account; users matching it get the admin role. */
    private final String adminEmail;

    /** Injects dependencies and normalizes the configured admin email for comparison. */
    public AuthService(AppUserRepository repository, ClockService clock, @Value("${app.admin-email:}") String adminEmail) {
        this.repository = repository;
        this.clock = clock;
        this.adminEmail = normalize(adminEmail);
    }

    /**
     * Finds a user by email.
     *
     * @param email raw email (normalized before lookup)
     * @return the matching user, or empty when the email is blank or no user exists
     */
    public Optional<AppUser> findByEmail(String email) {
        String normalizedEmail = normalize(email);
        return normalizedEmail.isBlank() ? Optional.empty() : repository.findByEmail(normalizedEmail);
    }

    /**
     * Signs a user in, creating the record on first sign-in (upsert).
     *
     * <p>Business rules: reuses the existing record by normalized email or creates a new
     * one with a generated id; sets the full name from the request when provided, else
     * keeps any existing name, else defaults to the email; grants {@code admin} when the
     * email matches the configured admin email, otherwise defaults new users to
     * {@code student} without overriding an existing role; applies notification defaults.
     *
     * @param request sign-in payload (email, optional full name)
     * @return the persisted user (side effect: a DB write via save)
     */
    public AppUser signIn(SignInRequest request) {
        String normalizedEmail = normalize(request.getEmail());
        String now = clock.now();
        // Reuse the existing user, or build a fresh record (compact generated id, blank avatar).
        AppUser user = repository.findByEmail(normalizedEmail).orElseGet(() -> {
            AppUser created = new AppUser();
            created.setId("user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            created.setCreatedDate(now);
            created.setAvatarUrl("");
            return created;
        });
        // Preserve existing name when fullName is not supplied on sign-in
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        } else if (user.getFullName() == null || user.getFullName().isBlank()) {
            // No name anywhere: fall back to the email as a display name.
            user.setFullName(normalizedEmail);
        }
        user.setEmail(normalizedEmail);
        // Admin email always wins; otherwise only assign a default role if none is set yet.
        if (normalizedEmail.equals(adminEmail)) {
            user.setRole("admin");
        } else if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("student");
        }
        applyNotificationDefaults(user);
        user.setUpdatedDate(now);
        return repository.save(user);
    }

    /**
     * Upserts a backend user from a server-verified identity (for example, an
     * Appwrite account whose JWT was validated by {@code AppwriteAuthService}).
     * Passwords are never received or stored here; identity and the admin
     * decision are determined upstream and only mapped onto the backend record.
     *
     * @param appwriteUserId external Appwrite account id (stored when present)
     * @param email          verified email (normalized)
     * @param fullName       verified display name (optional)
     * @param admin          whether the upstream verification determined admin status
     * @return the persisted user (side effect: a DB write via save)
     */
    public AppUser upsertFromVerifiedIdentity(String appwriteUserId, String email, String fullName, boolean admin) {
        String normalizedEmail = normalize(email);
        String now = clock.now();
        // Reuse the existing user, or build a fresh record.
        AppUser user = repository.findByEmail(normalizedEmail).orElseGet(() -> {
            AppUser created = new AppUser();
            created.setId("user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            created.setCreatedDate(now);
            created.setAvatarUrl("");
            return created;
        });
        // Link the external Appwrite id when supplied.
        if (appwriteUserId != null && !appwriteUserId.isBlank()) {
            user.setAppwriteUserId(appwriteUserId.trim());
        }
        // Resolve the name: prefer the supplied name, else keep existing, else use the email.
        String resolvedName = fullName == null ? "" : fullName.trim();
        if (resolvedName.isBlank()) {
            resolvedName = user.getFullName() == null || user.getFullName().isBlank()
                    ? normalizedEmail
                    : user.getFullName();
        }
        user.setFullName(resolvedName);
        user.setEmail(normalizedEmail);
        // Admin verification wins; otherwise default to student but never demote an existing staff role.
        if (admin) {
            user.setRole("admin");
        } else if (!"staff".equalsIgnoreCase(user.getRole())) {
            user.setRole("student");
        }
        applyNotificationDefaults(user);
        user.setUpdatedDate(now);
        return repository.save(user);
    }

    /**
     * Explicitly creates a new user record (always inserts a fresh row).
     *
     * @param email    raw email (normalized)
     * @param fullName display name; defaults to the email when blank
     * @param role     desired role; defaults to {@code student} when blank, otherwise lowercased
     * @return the persisted new user (side effect: a DB write via save)
     */
    public AppUser signUp(String email, String fullName, String role) {
        String normalizedEmail = normalize(email);
        String now = clock.now();
        AppUser user = new AppUser();
        user.setId("user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        user.setEmail(normalizedEmail);
        user.setFullName(fullName == null || fullName.isBlank() ? normalizedEmail : fullName.trim());
        user.setRole(role == null || role.isBlank() ? "student" : role.trim().toLowerCase(Locale.ROOT));
        user.setAvatarUrl("");
        user.setCreatedDate(now);
        user.setUpdatedDate(now);
        applyNotificationDefaults(user);
        return repository.save(user);
    }

    /**
     * Fills in any unset notification preferences with sensible defaults so every
     * persisted user has a complete preference set: email + webhook on, SMS off, and
     * an {@code ["all"]} category subscription. Existing (non-null) values are untouched.
     */
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

    /** Null-safe trim + lowercase (root locale) used to canonicalize emails for lookup/storage. */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
