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
    /** Per-request attribute key under which the resolved identity is cached so a request resolves auth only once. */
    private static final String REQUEST_CACHE_KEY = DemoAuthorizationService.class.getName() + ".resolved";

    /** Backend user store; used to look up accounts by email (demo-fallback path). */
    private final AppUserRepository users;
    /** Appwrite integration that verifies JWTs and checks admin-team membership (production path). */
    private final AppwriteAuthService appwrite;
    /** Upserts/syncs a local {@link AppUser} from a verified external identity. */
    private final AuthService authService;
    /** Normalized email that is treated as admin in the fallback case (and when no admin team is configured). */
    private final String adminEmail;
    /** When true, the legacy {@code X-Demo-User-Email} header is honored as a dev fallback; disable in production. */
    private final boolean demoFallbackEnabled;

    /**
     * @param users               backend user repository
     * @param appwrite            Appwrite auth/verification service
     * @param authService         service that upserts users from verified identities
     * @param adminEmail          configured admin email ({@code app.admin-email}); normalized to lowercase
     * @param demoFallbackEnabled whether the demo email header fallback is allowed ({@code app.auth.demo-fallback-enabled})
     */
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

    /**
     * Resolves the caller and enforces that they are an authenticated, non-suspended admin.
     *
     * @param demoEmailHeader value of the {@code X-Demo-User-Email} header (dev fallback only)
     * @return the resolved admin {@link AppUser}
     * @throws ForbiddenException if the session is invalid/expired, the account is suspended,
     *         or the caller is not an admin
     */
    public AppUser requireAdmin(String demoEmailHeader) {
        Resolved resolved = resolveCached(demoEmailHeader);
        // Reject expired/invalid sessions first (a present-but-bad JWT lands here).
        if (resolved.invalidSession()) {
            throw new ForbiddenException("Your session is invalid or has expired. Sign in again.");
        }
        // Suspended accounts are blocked even if they would otherwise be admins.
        if (resolved.suspended()) {
            throw new ForbiddenException("Your account has been suspended. Contact school staff.");
        }
        // Must be a known user AND carry the admin flag.
        if (resolved.user() == null || !resolved.admin()) {
            throw new ForbiddenException("Admin access is required.");
        }
        return resolved.user();
    }

    /**
     * Non-throwing admin check for the current caller.
     *
     * @param demoEmailHeader value of the demo email header (dev fallback only)
     * @return {@code true} only if the session is valid, the account is not suspended,
     *         a user resolved, and they are an admin
     */
    public boolean isAdmin(String demoEmailHeader) {
        Resolved resolved = resolveCached(demoEmailHeader);
        return !resolved.invalidSession() && !resolved.suspended() && resolved.user() != null && resolved.admin();
    }

    /**
     * Resolves the caller and enforces that they are an authenticated, non-suspended
     * user holding either the {@code admin} or {@code staff} role.
     *
     * @param demoEmailHeader value of the demo email header (dev fallback only)
     * @return the resolved staff/admin {@link AppUser}
     * @throws ForbiddenException if the session is invalid/expired, the account is
     *         suspended, no user resolved, or the role is neither admin nor staff
     */
    public AppUser requireStaffOrAdmin(String demoEmailHeader) {
        Resolved resolved = resolveCached(demoEmailHeader);
        if (resolved.invalidSession()) {
            throw new ForbiddenException("Your session is invalid or has expired. Sign in again.");
        }
        if (resolved.suspended()) {
            throw new ForbiddenException("Your account has been suspended. Contact school staff.");
        }
        if (resolved.user() == null) {
            throw new ForbiddenException("Staff or admin access is required.");
        }
        // Role gate: only admin or staff may proceed.
        String role = resolved.user().getRole();
        if (!"admin".equalsIgnoreCase(role) && !"staff".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Staff or admin access is required.");
        }
        return resolved.user();
    }

    /**
     * Non-throwing staff-or-admin check for the current caller.
     *
     * @param demoEmailHeader value of the demo email header (dev fallback only)
     * @return {@code true} only if the caller is a valid, non-suspended user whose role
     *         is admin or staff
     */
    public boolean isStaffOrAdmin(String demoEmailHeader) {
        Resolved resolved = resolveCached(demoEmailHeader);
        if (resolved.invalidSession() || resolved.suspended() || resolved.user() == null) return false;
        String role = resolved.user().getRole();
        return "admin".equalsIgnoreCase(role) || "staff".equalsIgnoreCase(role);
    }

    /** True when the current caller's account is suspended. */
    public boolean isSuspended(String demoEmailHeader) {
        return resolveCached(demoEmailHeader).suspended();
    }

    /** Returns the resolved backend user for the current request, or null when unauthenticated or suspended. */
    public AppUser currentUser(String demoEmailHeader) {
        Resolved resolved = resolveCached(demoEmailHeader);
        return resolved.invalidSession() || resolved.suspended() ? null : resolved.user();
    }

    /** Returns the verified email for the current caller, falling back to the demo header. Blank when suspended. */
    public String resolveEmail(String demoEmailHeader) {
        Resolved resolved = resolveCached(demoEmailHeader);
        if (resolved.invalidSession() || resolved.suspended()) {
            return "";
        }
        if (!resolved.email().isBlank()) {
            return resolved.email();
        }
        return normalize(demoEmailHeader);
    }

    /**
     * Resolves the caller once per HTTP request and memoizes the result in a
     * request-scoped attribute, so repeated authorization checks within the same
     * request avoid re-verifying the JWT / re-hitting the database.
     *
     * @param demoEmailHeader value of the demo email header (dev fallback only)
     * @return the cached or freshly computed {@link Resolved} identity
     */
    private Resolved resolveCached(String demoEmailHeader) {
        ServletRequestAttributes attributes = currentRequestAttributes();
        // Return the cached resolution if this request already computed one.
        if (attributes != null) {
            Object cached = attributes.getAttribute(REQUEST_CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof Resolved resolved) {
                return resolved;
            }
        }
        // First check this request: resolve and store for subsequent calls.
        Resolved resolved = resolve(demoEmailHeader);
        if (attributes != null) {
            attributes.setAttribute(REQUEST_CACHE_KEY, resolved, RequestAttributes.SCOPE_REQUEST);
        }
        return resolved;
    }

    /**
     * Core identity-resolution logic implementing the two-path strategy described on
     * the class Javadoc.
     *
     * <p>Production path: if a JWT is present and Appwrite is configured, verify the
     * token. An invalid token yields an {@code invalid} result (and is NOT allowed to
     * fall through to the demo header). On success, admin status comes from admin-team
     * membership when a team is configured, otherwise from matching the configured
     * admin email; the verified identity is upserted into the local user store.
     *
     * <p>Dev fallback: only when enabled, the demo email header is looked up; a found
     * user defaults to the {@code student} role when blank and is treated as admin when
     * their role is admin or their email matches the configured admin email.
     *
     * @param demoEmailHeader value of the demo email header (dev fallback only)
     * @return a {@link Resolved} describing the user, admin flag, email, session
     *         validity, and suspension state ({@code none()} when unauthenticated)
     */
    private Resolved resolve(String demoEmailHeader) {
        // --- Production path: verified Appwrite JWT ---
        String jwt = currentJwt();
        if (jwt != null && !jwt.isBlank() && appwrite.isConfigured()) {
            Optional<AppwriteIdentity> identity = appwrite.verify(jwt);
            // A present-but-unverifiable token is an invalid session (never falls back).
            if (identity.isEmpty()) {
                return Resolved.invalid();
            }
            AppwriteIdentity verified = identity.get();
            // Admin decision: team membership if an admin team exists, else admin-email match.
            boolean admin = appwrite.isAdminTeamConfigured()
                    ? appwrite.isMemberOfAdminTeam(jwt)
                    : verified.normalizedEmail().equals(adminEmail);
            // Sync the verified identity into the local user table and return it.
            AppUser user = authService.upsertFromVerifiedIdentity(verified.id(), verified.email(), verified.name(), admin);
            return new Resolved(user, admin, user.getEmail(), false, isSuspendedRole(user));
        }

        // --- Dev fallback path: X-Demo-User-Email header ---
        // Disabled in production so the header can never grant access.
        if (!demoFallbackEnabled) {
            return Resolved.none();
        }
        String email = normalize(demoEmailHeader);
        if (email.isBlank()) {
            return Resolved.none();
        }
        return users.findByEmail(email)
                .map(user -> {
                    // Backfill a default role for legacy/blank-role accounts.
                    if (user.getRole() == null || user.getRole().isBlank()) {
                        user.setRole("student");
                    }
                    return new Resolved(
                            user,
                            // Admin in fallback: explicit admin role or configured admin email.
                            "admin".equalsIgnoreCase(user.getRole()) || email.equals(adminEmail),
                            user.getEmail(),
                            false,
                            isSuspendedRole(user));
                })
                .orElseGet(Resolved::none);
    }

    /** @return true when the user exists and carries the {@code suspended} role. */
    private boolean isSuspendedRole(AppUser user) {
        return user != null && "suspended".equalsIgnoreCase(user.getRole());
    }

    /**
     * Extracts the bearer JWT from the current request.
     *
     * <p>Prefers the {@code X-Appwrite-JWT} header; otherwise parses a standard
     * {@code Authorization: Bearer <token>} header (scheme match is case-insensitive).
     *
     * @return the trimmed token, or {@code null} when there is no request or no token
     */
    private String currentJwt() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        // Preferred custom header.
        String header = request.getHeader("X-Appwrite-JWT");
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        // Fallback to the standard Authorization: Bearer header.
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return null;
    }

    /** @return the current {@link HttpServletRequest}, or {@code null} outside a request context. */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = currentRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    /** @return the current servlet request attributes, or {@code null} when not bound to a servlet request. */
    private ServletRequestAttributes currentRequestAttributes() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes ? servletAttributes : null;
    }

    /** @return a null-safe, trimmed, lowercased copy of {@code value} (empty string for null). */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Immutable snapshot of a resolved caller identity for one request.
     *
     * @param user           the resolved backend user, or {@code null} if unauthenticated
     * @param admin          whether the caller has admin privileges
     * @param email          the verified email (empty when unknown)
     * @param invalidSession true when a token was present but failed verification
     * @param suspended      true when the resolved account is suspended
     */
    private record Resolved(AppUser user, boolean admin, String email, boolean invalidSession, boolean suspended) {
        /** Unauthenticated/anonymous result: no user, valid (non-error) session. */
        static Resolved none() {
            return new Resolved(null, false, "", false, false);
        }

        /** Error result: a token was supplied but could not be verified. */
        static Resolved invalid() {
            return new Resolved(null, false, "", true, false);
        }
    }
}
