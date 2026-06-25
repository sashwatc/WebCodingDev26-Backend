package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import com.FBLA.WebCodingDev26Backend.service.AppwriteAuthService.AppwriteIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the caller identity and enforces admin-only access server-side.
 *
 * <p>Resolution order:
 * <ol>
 *   <li><b>Production path</b> &mdash; when an Appwrite project is configured and the request
 *       carries an Appwrite JWT (header {@code X-Appwrite-JWT} or {@code Authorization: Bearer}),
 *       the token is verified by Appwrite. Identity comes from the verified account and the admin
 *       decision comes from Appwrite team membership (or the documented admin-email fallback only
 *       when no admin team is configured). A present-but-invalid JWT is rejected and never falls
 *       through to the demo header.</li>
 *   <li><b>Development fallback</b> &mdash; only when {@code app.auth.demo-fallback-enabled=true},
 *       the legacy {@code X-Demo-User-Email} header is honored. Disable this in production so the
 *       header can never grant access.</li>
 * </ol>
 * The class name is retained for compatibility; behavior is no longer demo-only.
 */
@Service
public class DemoAuthorizationService {
    private static final String REQUEST_CACHE_KEY = DemoAuthorizationService.class.getName() + ".resolved";

    private final AppUserRepository users;
    private final AppwriteAuthService appwrite;
    private final AuthService authService;
    private final String adminEmail;
    private final boolean demoFallbackEnabled;

    public DemoAuthorizationService(
            AppUserRepository users,
            AppwriteAuthService appwrite,
            AuthService authService,
            @Value("${app.admin-email:avery.patel@pleasantvalley.edu}") String adminEmail,
            @Value("${app.auth.demo-fallback-enabled:true}") boolean demoFallbackEnabled
    ) {
        this.users = users;
        this.appwrite = appwrite;
        this.authService = authService;
        this.adminEmail = normalize(adminEmail);
        this.demoFallbackEnabled = demoFallbackEnabled;
    }

    public AppUser requireAdmin(String demoEmailHeader) {
        Resolved resolved = resolveCached(demoEmailHeader);
        if (resolved.invalidSession()) {
            throw new ForbiddenException("Your session is invalid or has expired. Sign in again.");
        }
        if (resolved.user() == null || !resolved.admin()) {
            throw new ForbiddenException("Admin access is required.");
        }
        return resolved.user();
    }

    public boolean isAdmin(String demoEmailHeader) {
        Resolved resolved = resolveCached(demoEmailHeader);
        return !resolved.invalidSession() && resolved.user() != null && resolved.admin();
    }

    /** Returns the resolved backend user for the current request, or null when unauthenticated. */
    public AppUser currentUser(String demoEmailHeader) {
        Resolved resolved = resolveCached(demoEmailHeader);
        return resolved.invalidSession() ? null : resolved.user();
    }

    /** Returns the verified email for the current caller, falling back to the demo header. */
    public String resolveEmail(String demoEmailHeader) {
        Resolved resolved = resolveCached(demoEmailHeader);
        if (resolved.invalidSession()) {
            return "";
        }
        if (!resolved.invalidSession() && !resolved.email().isBlank()) {
            return resolved.email();
        }
        return normalize(demoEmailHeader);
    }

    private Resolved resolveCached(String demoEmailHeader) {
        ServletRequestAttributes attributes = currentRequestAttributes();
        if (attributes != null) {
            Object cached = attributes.getAttribute(REQUEST_CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof Resolved resolved) {
                return resolved;
            }
        }
        Resolved resolved = resolve(demoEmailHeader);
        if (attributes != null) {
            attributes.setAttribute(REQUEST_CACHE_KEY, resolved, RequestAttributes.SCOPE_REQUEST);
        }
        return resolved;
    }

    private Resolved resolve(String demoEmailHeader) {
        String jwt = currentJwt();
        if (jwt != null && !jwt.isBlank() && appwrite.isConfigured()) {
            Optional<AppwriteIdentity> identity = appwrite.verify(jwt);
            if (identity.isEmpty()) {
                return Resolved.invalid();
            }
            AppwriteIdentity verified = identity.get();
            boolean admin = appwrite.isAdminTeamConfigured()
                    ? appwrite.isMemberOfAdminTeam(jwt)
                    : verified.normalizedEmail().equals(adminEmail);
            AppUser user = authService.upsertFromVerifiedIdentity(verified.id(), verified.email(), verified.name(), admin);
            return new Resolved(user, admin, user.getEmail(), false);
        }

        if (!demoFallbackEnabled) {
            return Resolved.none();
        }
        String email = normalize(demoEmailHeader);
        if (email.isBlank()) {
            return Resolved.none();
        }
        return users.findByEmail(email)
                .map(user -> {
                    if (user.getRole() == null || user.getRole().isBlank()) {
                        user.setRole("student");
                    }
                    return new Resolved(
                            user,
                            "admin".equalsIgnoreCase(user.getRole()) || email.equals(adminEmail),
                            user.getEmail(),
                            false);
                })
                .orElseGet(Resolved::none);
    }

    private String currentJwt() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String header = request.getHeader("X-Appwrite-JWT");
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = currentRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    private ServletRequestAttributes currentRequestAttributes() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes ? servletAttributes : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Resolved(AppUser user, boolean admin, String email, boolean invalidSession) {
        static Resolved none() {
            return new Resolved(null, false, "", false);
        }

        static Resolved invalid() {
            return new Resolved(null, false, "", true);
        }
    }
}
