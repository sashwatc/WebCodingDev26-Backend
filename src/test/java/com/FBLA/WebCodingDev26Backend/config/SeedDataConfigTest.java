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

/**
 * Unit tests for {@link SeedDataConfig}, the {@link CommandLineRunner} that populates demo/showcase
 * data on startup.
 *
 * <p>Each repository is a Mockito mock (no real database). The tests build the runner directly via
 * {@code new SeedDataConfig().seedData(...)} and invoke {@code runner.run()}, then verify exactly
 * which save/saveAll calls the seeding logic makes. They cover: a fresh empty database, a database
 * that already has some found items (top-up of missing showcase rows only), protection of
 * pre-existing non-demo rows that happen to share seed IDs, and the disabled-seeding switch.</p>
 */
@ExtendWith(MockitoExtension.class)
class SeedDataConfigTest {
    // Mocked found-item repository; its count()/findById() drive the seeding branch decisions.
    @Mock
    private FoundItemRepository foundItems;
    // Mocked lost-report repository used to detect/insert missing showcase lost reports.
    @Mock
    private LostReportRepository lostReports;
    // Mocked claim repository used to detect/insert missing showcase claims.
    @Mock
    private ClaimRepository claims;
    // Mocked notification repository used to detect/insert missing showcase notifications.
    @Mock
    private NotificationRepository notifications;
    // Mocked audit-log repository used to record the seeding action.
    @Mock
    private AuditLogRepository auditLogs;
    // Mocked user repository used to insert the demo user accounts.
    @Mock
    private AppUserRepository users;

    /**
     * Scenario: seeding is enabled and the database is completely empty (count == 0).
     * Arrange: stub found-item count to 0 and every findById to empty so all seed rows are treated
     * as missing.
     * Act: run the seeding runner.
     * Assert: the bulk saveAll path runs for found items, and the full demo data set is written —
     * 12 found items, 12 lost reports, 10 claims, 10 notifications, an audit log, and 6 users.
     * Passing proves a first-time boot fully populates the showcase dataset.
     */
    @Test
    void seedDataLoadsWhenEnabledAndFoundItemsAreEmpty() throws Exception {
        when(foundItems.count()).thenReturn(0L); // empty DB -> full initial seed path
        stubMissingSeedRows(); // every seed row reported absent so all get saved

        CommandLineRunner runner = new SeedDataConfig()
                .seedData(foundItems, lostReports, claims, notifications, auditLogs, users, true);

        runner.run();

        verify(foundItems).saveAll(ArgumentMatchers.<FoundItem>anyIterable()); // bulk insert of base catalog
        verify(foundItems, times(12)).save(any(FoundItem.class)); // showcase found items individually saved
        verify(lostReports, times(12)).save(any(LostReport.class));
        verify(claims, times(10)).save(any(Claim.class));
        verify(notifications, times(10)).save(any(Notification.class));
        verify(auditLogs).save(any(AuditLog.class)); // one audit entry recording the seed run
        verify(users, times(6)).save(any(AppUser.class)); // six demo accounts
    }

    /**
     * Scenario: seeding is enabled but the database already contains found items (count == 2),
     * representing a re-run/top-up rather than a first boot.
     * Arrange: count returns 2, and all seed rows are reported missing.
     * Act: run the seeding runner.
     * Assert: the bulk saveAll base-catalog path is skipped (never called) since items already
     * exist, yet missing individual showcase rows are still topped up. The lower lost/claim/
     * notification counts (9/7/8) reflect that only the showcase additions, not the full base set,
     * are inserted on a non-empty database. Passing proves re-runs add only missing showcase data.
     */
    @Test
    void seedDataAddsMissingShowcaseContentWhenDatabaseAlreadyHasFoundItems() throws Exception {
        when(foundItems.count()).thenReturn(2L); // non-empty DB -> top-up path, not full bulk insert
        stubMissingSeedRows();

        CommandLineRunner runner = new SeedDataConfig()
                .seedData(foundItems, lostReports, claims, notifications, auditLogs, users, true);

        runner.run();

        verify(foundItems, never()).saveAll(ArgumentMatchers.<FoundItem>anyIterable()); // base catalog NOT re-bulk-inserted
        verify(foundItems, times(12)).save(any(FoundItem.class)); // showcase found items still topped up
        verify(lostReports, times(9)).save(any(LostReport.class)); // only showcase lost reports added
        verify(claims, times(7)).save(any(Claim.class));
        verify(notifications, times(8)).save(any(Notification.class));
    }

    /**
     * Scenario: seed IDs collide with pre-existing rows that are NOT demo rows (isDemo == false),
     * e.g. real data a user created.
     * Arrange: count is 2; findById for items/reports/claims returns existing non-demo entities,
     * while notifications are still missing.
     * Act: run the seeding runner.
     * Assert: the seeder never overwrites the existing non-demo found items, lost reports, or claims
     * (no save calls for them), but does insert the genuinely missing notifications (8). Passing
     * proves seeding is non-destructive toward real user data sharing seed IDs.
     */
    @Test
    void seedDataDoesNotOverwriteNonDemoRowsWithSameIds() throws Exception {
        // Pre-existing rows flagged as real (non-demo) data that must be preserved.
        FoundItem existingItem = new FoundItem();
        existingItem.setIsDemo(false);
        LostReport existingReport = new LostReport();
        existingReport.setIsDemo(false);
        Claim existingClaim = new Claim();
        existingClaim.setIsDemo(false);

        when(foundItems.count()).thenReturn(2L);
        // findById returns the existing non-demo rows so the seeder sees a conflict and backs off.
        when(foundItems.findById(anyString())).thenReturn(Optional.of(existingItem));
        when(lostReports.findById(anyString())).thenReturn(Optional.of(existingReport));
        when(claims.findById(anyString())).thenReturn(Optional.of(existingClaim));
        when(notifications.findById(anyString())).thenReturn(Optional.empty()); // notifications still missing

        CommandLineRunner runner = new SeedDataConfig()
                .seedData(foundItems, lostReports, claims, notifications, auditLogs, users, true);

        runner.run();

        verify(foundItems, never()).save(any(FoundItem.class)); // existing non-demo item untouched
        verify(lostReports, never()).save(any(LostReport.class)); // existing non-demo report untouched
        verify(claims, never()).save(any(Claim.class)); // existing non-demo claim untouched
        verify(notifications, times(8)).save(any(Notification.class)); // missing notifications still seeded
    }

    /**
     * Scenario: seeding is disabled (enabled flag = false).
     * Arrange: build the runner with the trailing enabled argument set to false.
     * Act: run the seeding runner.
     * Assert: the seeder short-circuits immediately — it never even queries count() nor performs
     * the bulk saveAll. Passing proves the {@code app.seed.enabled=false} switch fully disables
     * seeding.
     */
    @Test
    void seedDataSkipsWhenDisabled() throws Exception {
        CommandLineRunner runner = new SeedDataConfig()
                .seedData(foundItems, lostReports, claims, notifications, auditLogs, users, false);

        runner.run();

        verify(foundItems, never()).count(); // disabled -> no DB inspection
        verify(foundItems, never()).saveAll(ArgumentMatchers.<FoundItem>anyIterable()); // and no writes
    }

    // Helper: stub every repository's findById to return empty so the seeder treats all seed rows
    // as missing and therefore attempts to insert each one.
    private void stubMissingSeedRows() {
        when(foundItems.findById(anyString())).thenReturn(Optional.empty());
        when(lostReports.findById(anyString())).thenReturn(Optional.empty());
        when(claims.findById(anyString())).thenReturn(Optional.empty());
        when(notifications.findById(anyString())).thenReturn(Optional.empty());
    }
}
