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

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private AppUserRepository repository;

    @Test
    void signInCreatesStudentWhenEmailDoesNotExist() {
        ClockService clock = new ClockService();
        AuthService service = new AuthService(repository, clock, "avery.patel@pleasantvalley.edu");
        SignInRequest request = request("Riley Chen", "RILEY.CHEN@pleasantvalley.edu");

        when(repository.findByEmail("riley.chen@pleasantvalley.edu")).thenReturn(Optional.empty());
        when(repository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser user = service.signIn(request);

        assertThat(user.getEmail()).isEqualTo("riley.chen@pleasantvalley.edu");
        assertThat(user.getFullName()).isEqualTo("Riley Chen");
        assertThat(user.getRole()).isEqualTo("student");
        assertThat(user.getId()).startsWith("user_");
    }

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

        when(repository.findByEmail("riley.chen@pleasantvalley.edu")).thenReturn(Optional.of(existing));
        when(repository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser user = service.signIn(request("Riley C.", "riley.chen@pleasantvalley.edu"));

        assertThat(user.getId()).isEqualTo("user_existing");
        assertThat(user.getFullName()).isEqualTo("Riley C.");
        assertThat(user.getCreatedDate()).isEqualTo("2026-03-10T10:00:00Z");
    }

    @Test
    void signInAssignsAdminOnlyForConfiguredEmail() {
        ClockService clock = new ClockService();
        AuthService service = new AuthService(repository, clock, "avery.patel@pleasantvalley.edu");

        when(repository.findByEmail("avery.patel@pleasantvalley.edu")).thenReturn(Optional.empty());
        when(repository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser user = service.signIn(request("Avery Patel", "avery.patel@pleasantvalley.edu"));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());
        assertThat(user.getRole()).isEqualTo("admin");
        assertThat(captor.getValue().getRole()).isEqualTo("admin");
    }

    private SignInRequest request(String fullName, String email) {
        SignInRequest request = new SignInRequest();
        request.setFullName(fullName);
        request.setEmail(email);
        return request;
    }
}
