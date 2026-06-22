package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DemoAuthorizationService {
    private final AppUserRepository users;
    private final String adminEmail;

    public DemoAuthorizationService(AppUserRepository users, @Value("${app.admin-email:avery.patel@pleasantvalley.edu}") String adminEmail) {
        this.users = users;
        this.adminEmail = normalize(adminEmail);
    }

    public AppUser requireAdmin(String emailHeader) {
        String email = normalize(emailHeader);
        if (email.isBlank()) {
            throw new ForbiddenException("Admin access is required.");
        }

        AppUser user = users.findByEmail(email)
                .orElseThrow(() -> new ForbiddenException("Admin access is required."));
        if (!"admin".equalsIgnoreCase(user.getRole()) && !email.equals(adminEmail)) {
            throw new ForbiddenException("Admin access is required.");
        }
        return user;
    }

    public boolean isAdmin(String emailHeader) {
        try {
            requireAdmin(emailHeader);
            return true;
        } catch (ForbiddenException exception) {
            return false;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
