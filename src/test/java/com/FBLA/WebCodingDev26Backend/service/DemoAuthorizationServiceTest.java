package com.FBLA.WebCodingDev26Backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import com.FBLA.WebCodingDev26Backend.service.AppwriteAuthService.AppwriteIdentity;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Unit tests for {@link DemoAuthorizationService}, which resolves the caller's identity/role from
 * either a verified Appwrite JWT (preferred) or, when enabled, a demo {@code X-Demo-User-Email}
 * header fallback.
 *
 * <p>Collaborators (user repository, Appwrite verifier, auth service) are mocked. Tests bind a fake
 * servlet request carrying the JWT header via {@link RequestContextHolder} and assert the security
 * decisions: verified admins are allowed, verified non-admins and invalid/tampered JWTs are
 * rejected (with no fallback to the demo header), the demo header is honored only when the fallback
 * flag is enabled, and admin checks never touch the user repository on the JWT path.</p>
 */
@ExtendWith(MockitoExtension.class)
class DemoAuthorizationServiceTest {
    // Email the service treats as the privileged admin account.
    private static final String ADMIN_EMAIL = "avery.patel@pleasantvalley.edu";

    // Mocked user repository (consulted only on the demo-header fallback path).
    @Mock
    private AppUserRepository users;
    // Mocked Appwrite auth integration: JWT verification and admin-team membership checks.
    @Mock
    private AppwriteAuthService appwrite;
    // Mocked auth service used to upsert a local account from a verified Appwrite identity.
    @Mock
    private AuthService authService;

    // Clear the bound servlet request after each test so leftover request attributes never leak
    // into the next test's RequestContextHolder.
    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * Scenario: a request carries a valid JWT for a user who is a member of the admin team.
     * Arrange: bind the JWT header; stub Appwrite as configured, verifying the token to an identity,
     * with admin-team membership true; stub the auth service to upsert and return an admin user.
     * Act: call requireAdmin(null) (email arg ignored since JWT wins).
     * Assert: the resolved user has role "admin" and the user repository is never consulted (the JWT
     * path bypasses the demo-header lookup). Passing proves verified admins gain admin access.
     */
    @Test
    void verifiedAdminJwtGrantsAdminAccess() {
        bindRequestWithJwt("valid.jwt.token"); // request presents a JWT
        when(appwrite.isConfigured()).thenReturn(true);
        when(appwrite.verify("valid.jwt.token")).thenReturn(Optional.of(new AppwriteIdentity("acc_1", "ADMIN@pleasantvalley.edu", "Avery"))); // token verifies
        when(appwrite.isAdminTeamConfigured()).thenReturn(true);
        when(appwrite.isMemberOfAdminTeam("valid.jwt.token")).thenReturn(true); // caller is in admin team
        when(authService.upsertFromVerifiedIdentity(eq("acc_1"), eq("ADMIN@pleasantvalley.edu"), eq("Avery"), eq(true)))
                .thenReturn(user("admin@pleasantvalley.edu", "admin")); // local account resolved as admin

        AppUser resolved = service(true).requireAdmin(null);

        assertThat(resolved.getRole()).isEqualTo("admin"); // admin access granted
        verifyNoInteractions(users); // demo-header repo lookup never used on JWT path
    }

    /**
     * Scenario: a request carries a valid JWT, but the user is NOT a member of the admin team.
     * Arrange: bind the JWT; Appwrite verifies it to a student identity with admin-team membership
     * false; auth service upserts a "student" account.
     * Act: check isAdmin and call requireAdmin.
     * Assert: isAdmin returns false and requireAdmin throws {@link ForbiddenException}. Passing
     * proves a valid but non-privileged token cannot reach admin routes.
     */
    @Test
    void verifiedNonAdminJwtIsRejectedForAdminRoutes() {
        bindRequestWithJwt("valid.jwt.token");
        when(appwrite.isConfigured()).thenReturn(true);
        when(appwrite.verify("valid.jwt.token")).thenReturn(Optional.of(new AppwriteIdentity("acc_2", "student@pleasantvalley.edu", "Riley"))); // verifies as student
        when(appwrite.isAdminTeamConfigured()).thenReturn(true);
        when(appwrite.isMemberOfAdminTeam("valid.jwt.token")).thenReturn(false); // NOT in admin team
        when(authService.upsertFromVerifiedIdentity(any(), any(), any(), anyBoolean()))
                .thenReturn(user("student@pleasantvalley.edu", "student"));

        DemoAuthorizationService service = service(true);

        assertThat(service.isAdmin(null)).isFalse(); // not recognized as admin
        assertThatThrownBy(() -> service.requireAdmin(null)).isInstanceOf(ForbiddenException.class); // admin route blocked
    }

    /**
     * Scenario: a request presents a tampered/invalid JWT while the demo-header fallback is enabled.
     * Arrange: bind a "tampered.jwt"; Appwrite is configured but verify() returns empty (rejected).
     * Pass the admin email as the demo-header value to confirm it is NOT trusted.
     * Act: call requireAdmin and resolveEmail with the admin email.
     * Assert: requireAdmin throws {@link ForbiddenException}, resolveEmail returns blank, the user
     * repo is never touched, and no identity upsert occurs. Passing proves an invalid token is a
     * hard failure and never silently falls back to the spoofable demo header.
     */
    @Test
    void invalidJwtIsRejectedAndNeverFallsBackToDemoHeader() {
        bindRequestWithJwt("tampered.jwt"); // bad token present
        when(appwrite.isConfigured()).thenReturn(true);
        when(appwrite.verify("tampered.jwt")).thenReturn(Optional.empty()); // verification fails

        DemoAuthorizationService service = service(true); // fallback enabled, yet must not be used

        assertThatThrownBy(() -> service.requireAdmin(ADMIN_EMAIL)).isInstanceOf(ForbiddenException.class); // rejected
        assertThat(service.resolveEmail(ADMIN_EMAIL)).isBlank(); // no identity resolved
        verifyNoInteractions(users); // demo-header lookup never attempted
        verify(authService, never()).upsertFromVerifiedIdentity(any(), any(), any(), anyBoolean()); // no upsert
    }

    /**
     * Scenario: a demo header is supplied but the demo fallback is disabled (production-like).
     * Arrange: build the service with demoFallbackEnabled=false; no JWT is bound.
     * Act: call requireAdmin with the admin email passed as the demo header.
     * Assert: requireAdmin throws {@link ForbiddenException} and the user repo is never queried.
     * Passing proves the demo-header shortcut is fully inert when the fallback is off.
     */
    @Test
    void demoHeaderIsIgnoredWhenFallbackDisabled() {
        DemoAuthorizationService service = service(false); // fallback disabled

        assertThatThrownBy(() -> service.requireAdmin(ADMIN_EMAIL)).isInstanceOf(ForbiddenException.class); // header ignored
        verifyNoInteractions(users); // no repo lookup
    }

    /**
     * Scenario: no JWT is present, the demo fallback is enabled, and the demo header names the admin.
     * Arrange: stub the user repo to return an admin account for the admin email.
     * Act: call requireAdmin with the admin email (acting as the demo header value).
     * Assert: the resolved user's email is the admin email. Passing proves that, with fallback on,
     * the demo header successfully resolves a real admin account for local/demo use.
     */
    @Test
    void demoAdminHeaderGrantsAccessWhenFallbackEnabled() {
        when(users.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(user(ADMIN_EMAIL, "admin"))); // header email maps to admin

        AppUser resolved = service(true).requireAdmin(ADMIN_EMAIL);

        assertThat(resolved.getEmail()).isEqualTo(ADMIN_EMAIL); // admin resolved via demo header
    }

    // Helper: construct the service under test with the configured admin email and the given
    // demo-fallback toggle.
    private DemoAuthorizationService service(boolean demoFallbackEnabled) {
        return new DemoAuthorizationService(users, appwrite, authService, ADMIN_EMAIL, demoFallbackEnabled);
    }

    // Helper: bind a fake servlet request carrying the given JWT in the X-Appwrite-JWT header so the
    // service can read it from the current request context.
    private void bindRequestWithJwt(String jwt) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Appwrite-JWT", jwt);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // Helper: build an AppUser fixture with the given email and role.
    private AppUser user(String email, String role) {
        AppUser user = new AppUser();
        user.setId("user_" + role);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
