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

@ExtendWith(MockitoExtension.class)
class DemoAuthorizationServiceTest {
    private static final String ADMIN_EMAIL = "avery.patel@pleasantvalley.edu";

    @Mock
    private AppUserRepository users;
    @Mock
    private AppwriteAuthService appwrite;
    @Mock
    private AuthService authService;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void verifiedAdminJwtGrantsAdminAccess() {
        bindRequestWithJwt("valid.jwt.token");
        when(appwrite.isConfigured()).thenReturn(true);
        when(appwrite.verify("valid.jwt.token")).thenReturn(Optional.of(new AppwriteIdentity("acc_1", "ADMIN@pleasantvalley.edu", "Avery")));
        when(appwrite.isAdminTeamConfigured()).thenReturn(true);
        when(appwrite.isMemberOfAdminTeam("valid.jwt.token")).thenReturn(true);
        when(authService.upsertFromVerifiedIdentity(eq("acc_1"), eq("ADMIN@pleasantvalley.edu"), eq("Avery"), eq(true)))
                .thenReturn(user("admin@pleasantvalley.edu", "admin"));

        AppUser resolved = service(true).requireAdmin(null);

        assertThat(resolved.getRole()).isEqualTo("admin");
        verifyNoInteractions(users);
    }

    @Test
    void verifiedNonAdminJwtIsRejectedForAdminRoutes() {
        bindRequestWithJwt("valid.jwt.token");
        when(appwrite.isConfigured()).thenReturn(true);
        when(appwrite.verify("valid.jwt.token")).thenReturn(Optional.of(new AppwriteIdentity("acc_2", "student@pleasantvalley.edu", "Riley")));
        when(appwrite.isAdminTeamConfigured()).thenReturn(true);
        when(appwrite.isMemberOfAdminTeam("valid.jwt.token")).thenReturn(false);
        when(authService.upsertFromVerifiedIdentity(any(), any(), any(), anyBoolean()))
                .thenReturn(user("student@pleasantvalley.edu", "student"));

        DemoAuthorizationService service = service(true);

        assertThat(service.isAdmin(null)).isFalse();
        assertThatThrownBy(() -> service.requireAdmin(null)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void invalidJwtIsRejectedAndNeverFallsBackToDemoHeader() {
        bindRequestWithJwt("tampered.jwt");
        when(appwrite.isConfigured()).thenReturn(true);
        when(appwrite.verify("tampered.jwt")).thenReturn(Optional.empty());

        DemoAuthorizationService service = service(true);

        assertThatThrownBy(() -> service.requireAdmin(ADMIN_EMAIL)).isInstanceOf(ForbiddenException.class);
        assertThat(service.resolveEmail(ADMIN_EMAIL)).isBlank();
        verifyNoInteractions(users);
        verify(authService, never()).upsertFromVerifiedIdentity(any(), any(), any(), anyBoolean());
    }

    @Test
    void demoHeaderIsIgnoredWhenFallbackDisabled() {
        DemoAuthorizationService service = service(false);

        assertThatThrownBy(() -> service.requireAdmin(ADMIN_EMAIL)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(users);
    }

    @Test
    void demoAdminHeaderGrantsAccessWhenFallbackEnabled() {
        when(users.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(user(ADMIN_EMAIL, "admin")));

        AppUser resolved = service(true).requireAdmin(ADMIN_EMAIL);

        assertThat(resolved.getEmail()).isEqualTo(ADMIN_EMAIL);
    }

    private DemoAuthorizationService service(boolean demoFallbackEnabled) {
        return new DemoAuthorizationService(users, appwrite, authService, ADMIN_EMAIL, demoFallbackEnabled);
    }

    private void bindRequestWithJwt(String jwt) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Appwrite-JWT", jwt);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private AppUser user(String email, String role) {
        AppUser user = new AppUser();
        user.setId("user_" + role);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
