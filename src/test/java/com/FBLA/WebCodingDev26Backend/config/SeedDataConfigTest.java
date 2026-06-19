package com.FBLA.WebCodingDev26Backend.config;

import static org.mockito.ArgumentMatchers.any;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

        CommandLineRunner runner = new SeedDataConfig()
                .seedData(foundItems, lostReports, claims, notifications, auditLogs, users, true);

        runner.run();

        verify(foundItems).saveAll(any(List.class));
        verify(lostReports).save(any(LostReport.class));
        verify(claims).save(any(Claim.class));
        verify(notifications).save(any(Notification.class));
        verify(auditLogs).save(any(AuditLog.class));
        verify(users, times(2)).save(any(AppUser.class));
    }

    @Test
    void seedDataSkipsWhenDatabaseAlreadyHasFoundItems() throws Exception {
        when(foundItems.count()).thenReturn(2L);

        CommandLineRunner runner = new SeedDataConfig()
                .seedData(foundItems, lostReports, claims, notifications, auditLogs, users, true);

        runner.run();

        verify(foundItems, never()).saveAll(any(List.class));
        verify(lostReports, never()).save(any(LostReport.class));
    }

    @Test
    void seedDataSkipsWhenDisabled() throws Exception {
        CommandLineRunner runner = new SeedDataConfig()
                .seedData(foundItems, lostReports, claims, notifications, auditLogs, users, false);

        runner.run();

        verify(foundItems, never()).count();
        verify(foundItems, never()).saveAll(any(List.class));
    }
}
