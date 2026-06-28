package com.FBLA.WebCodingDev26Backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.FBLA.WebCodingDev26Backend.dto.SignInRequest;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AuthService#signIn} sign-in/upsert behavior.
 *
 * <p>The {@link AppUserRepository} is mocked; a real {@link ClockService} is used for timestamps.
 * Each test constructs the service with a configured admin email
 * ("avery.patel@pleasantvalley.edu") and verifies email normalization (lower-casing), creation of
 * new student accounts, idempotent updates to existing accounts, and that the admin role is granted
 * only to the configured admin email.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    // Mocked user repository so sign-in lookups/saves are controlled without a database.
    @Mock
    private AppUserRepository repository;

    /**
     * Scenario: a brand-new user signs in with a mixed-case email.
     * Arrange: repository reports no existing user for the normalized email; save echoes back the
     * saved entity. The request email "RILEY.CHEN@..." is intentionally upper-cased to exercise
     * normalization.
     * Act: call signIn.
     * Assert: a new account is created with the lower-cased email, the provided full name, the
     * default "student" role, and a generated id prefixed "user_". Passing proves first-time
     * sign-in creates a normalized student account.
     */
    @Test
    void signInCreatesStudentWhenEmailDoesNotExist() {
        ClockService clock = new ClockService();
        AuthService service = new AuthService(repository, clock, "avery.patel@pleasantvalley.edu");
        SignInRequest request = request("Riley Chen", "RILEY.CHEN@pleasantvalley.edu"); // mixed case -> must normalize

        when(repository.findByEmail("riley.chen@pleasantvalley.edu")).thenReturn(Optional.empty()); // no existing user
        when(repository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0)); // return saved entity

        AppUser user = service.signIn(request);

        assertThat(user.getEmail()).isEqualTo("riley.chen@pleasantvalley.edu"); // stored lower-cased
        assertThat(user.getFullName()).isEqualTo("Riley Chen");
        assertThat(user.getRole()).isEqualTo("student"); // default role for non-admin email
        assertThat(user.getId()).startsWith("user_"); // generated id format
    }

    /**
     * Scenario: an already-registered user signs in again, e.g. with an updated display name.
     * Arrange: repository returns an existing account (id "user_existing", created 2026-03-10);
     * save echoes back the entity.
     * Act: call signIn with the same email but a new name "Riley C.".
     * Assert: the existing id is preserved (no new account), the full name is updated, and the
     * original createdDate is retained. Passing proves sign-in updates in place and never clobbers
     * identity/creation metadata.
     */
    @Test
    void signInUpdatesExistingUserByEmail() {
        ClockService clock = new ClockService();
        AuthService service = new AuthService(repository, clock, "avery.patel@pleasantvalley.edu");
        AppUser existing = new AppUser();
        existing.setId("user_existing");
        existing.setEmail("riley.chen@pleasantvalley.edu");
        existing.setFullName("Riley Chen");
        existing.setRole("student");
        existing.setCreatedDate("2026-03-10T10:00:00Z");

        when(repository.findByEmail("riley.chen@pleasantvalley.edu")).thenReturn(Optional.of(existing)); // pre-existing account
        when(repository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser user = service.signIn(request("Riley C.", "riley.chen@pleasantvalley.edu"));

        assertThat(user.getId()).isEqualTo("user_existing"); // same identity, not a new account
        assertThat(user.getFullName()).isEqualTo("Riley C."); // name updated to new value
        assertThat(user.getCreatedDate()).isEqualTo("2026-03-10T10:00:00Z"); // original creation timestamp preserved
    }

    /**
     * Scenario: the configured admin email signs in for the first time.
     * Arrange: repository reports no existing user for the admin email; save echoes the entity.
     * Act: call signIn with the admin email.
     * Assert: the returned user has role "admin", and the captured saved entity also has role
     * "admin" (proving the role is persisted, not just set on the return value). Passing proves the
     * admin role is granted exclusively to the configured admin email.
     */
    @Test
    void signInAssignsAdminOnlyForConfiguredEmail() {
        ClockService clock = new ClockService();
        AuthService service = new AuthService(repository, clock, "avery.patel@pleasantvalley.edu"); // configured admin

        when(repository.findByEmail("avery.patel@pleasantvalley.edu")).thenReturn(Optional.empty());
        when(repository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser user = service.signIn(request("Avery Patel", "avery.patel@pleasantvalley.edu"));

        // Capture what was actually persisted to confirm the admin role was saved, not just returned.
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());
        assertThat(user.getRole()).isEqualTo("admin"); // returned user is admin
        assertThat(captor.getValue().getRole()).isEqualTo("admin"); // persisted user is admin
    }

    // Helper: build a SignInRequest DTO with the given name and email.
    private SignInRequest request(String fullName, String email) {
        SignInRequest request = new SignInRequest();
        request.setFullName(fullName);
        request.setEmail(email);
        return request;
    }
}
