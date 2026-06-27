package com.FBLA.WebCodingDev26Backend.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;

@ExtendWith(MockitoExtension.class)
class SeedDataConfigTest {
    @Mock
    private FoundItemRepository foundItems;
    @Mock
    private LostReportRepository lostReports;
    @Mock
    private ClaimRepository claims;
    @Mock
    private NotificationRepository notifications;
    @Mock
    private AuditLogRepository auditLogs;
    @Mock
    private AppUserRepository users;

    @Test
    void seedDataLoadsWhenEnabledAndFoundItemsAreEmpty() throws Exception {
        when(foundItems.count()).thenReturn(0L);
        stubMissingSeedRows();

        CommandLineRunner runner = new SeedDataConfig()
                .seedData(foundItems, lostReports, claims, notifications, auditLogs, users, true);

        runner.run();

        verify(foundItems).saveAll(ArgumentMatchers.<FoundItem>anyIterable());
        verify(foundItems, times(12)).save(any(FoundItem.class));
        verify(lostReports, times(12)).save(any(LostReport.class));
        verify(claims, times(10)).save(any(Claim.class));
        verify(notifications, times(10)).save(any(Notification.class));
        verify(auditLogs).save(any(AuditLog.class));
        verify(users, times(6)).save(any(AppUser.class));
    }

    @Test
    void seedDataAddsMissingShowcaseContentWhenDatabaseAlreadyHasFoundItems() throws Exception {
        when(foundItems.count()).thenReturn(2L);
        stubMissingSeedRows();

        CommandLineRunner runner = new SeedDataConfig()
                .seedData(foundItems, lostReports, claims, notifications, auditLogs, users, true);

        runner.run();

        verify(foundItems, never()).saveAll(ArgumentMatchers.<FoundItem>anyIterable());
        verify(foundItems, times(12)).save(any(FoundItem.class));
        verify(lostReports, times(9)).save(any(LostReport.class));
        verify(claims, times(7)).save(any(Claim.class));
        verify(notifications, times(8)).save(any(Notification.class));
    }

    @Test
    void seedDataDoesNotOverwriteNonDemoRowsWithSameIds() throws Exception {
        FoundItem existingItem = new FoundItem();
        existingItem.setIsDemo(false);
        LostReport existingReport = new LostReport();
        existingReport.setIsDemo(false);
        Claim existingClaim = new Claim();
        existingClaim.setIsDemo(false);

        when(foundItems.count()).thenReturn(2L);
        when(foundItems.findById(anyString())).thenReturn(Optional.of(existingItem));
        when(lostReports.findById(anyString())).thenReturn(Optional.of(existingReport));
        when(claims.findById(anyString())).thenReturn(Optional.of(existingClaim));
        when(notifications.findById(anyString())).thenReturn(Optional.empty());

        CommandLineRunner runner = new SeedDataConfig()
                .seedData(foundItems, lostReports, claims, notifications, auditLogs, users, true);

        runner.run();

        verify(foundItems, never()).save(any(FoundItem.class));
        verify(lostReports, never()).save(any(LostReport.class));
        verify(claims, never()).save(any(Claim.class));
        verify(notifications, times(8)).save(any(Notification.class));
    }

    @Test
    void seedDataSkipsWhenDisabled() throws Exception {
        CommandLineRunner runner = new SeedDataConfig()
                .seedData(foundItems, lostReports, claims, notifications, auditLogs, users, false);

        runner.run();

        verify(foundItems, never()).count();
        verify(foundItems, never()).saveAll(ArgumentMatchers.<FoundItem>anyIterable());
    }

    private void stubMissingSeedRows() {
        when(foundItems.findById(anyString())).thenReturn(Optional.empty());
        when(lostReports.findById(anyString())).thenReturn(Optional.empty());
        when(claims.findById(anyString())).thenReturn(Optional.empty());
        when(notifications.findById(anyString())).thenReturn(Optional.empty());
    }
}
