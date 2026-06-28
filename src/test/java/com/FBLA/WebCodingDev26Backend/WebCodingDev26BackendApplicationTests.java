package com.FBLA.WebCodingDev26Backend;

import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import com.FBLA.WebCodingDev26Backend.repository.AssetRegistryRecordRepository;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.CampusZoneRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.CustodyEventRepository;
import com.FBLA.WebCodingDev26Backend.repository.CaseMessageRepository;
import com.FBLA.WebCodingDev26Backend.repository.EventRecoveryHubRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationDeliveryRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import com.FBLA.WebCodingDev26Backend.repository.PreventionAlertRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryCaseRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryNodeRepository;
import com.FBLA.WebCodingDev26Backend.repository.ReturnPassRepository;
import com.FBLA.WebCodingDev26Backend.repository.SavedSearchRepository;
import com.FBLA.WebCodingDev26Backend.repository.SupportTicketRepository;
import com.FBLA.WebCodingDev26Backend.repository.SystemSettingRepository;
import com.FBLA.WebCodingDev26Backend.repository.WatchedItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Smoke test for the whole application's Spring context.
 *
 * <p>Verifies that the full Spring Boot {@link org.springframework.context.ApplicationContext}
 * boots successfully with every bean wired together. The real MongoDB auto-configuration is
 * deliberately excluded (via {@code spring.autoconfigure.exclude}) and seed data loading is
 * turned off ({@code app.seed.enabled=false}) so the test does not require a live database.
 * Every repository plus {@link MongoTemplate} is supplied as a Mockito bean so that beans which
 * depend on the persistence layer can still be constructed without a real Mongo connection.</p>
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
				+ "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration,"
				+ "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration",
		"app.seed.enabled=false"
})
class WebCodingDev26BackendApplicationTests {
	// Mocked low-level Mongo access object so persistence-aware beans can be created without a DB.
	@MockitoBean
	private MongoTemplate mongoTemplate;
	// The remaining fields mock every Spring Data repository in the app. Each must be a mock bean
	// so that the services/controllers that inject them can be instantiated during context startup
	// even though no real Mongo-backed repository implementations are created.
	// Stores case discussion messages.
	@MockitoBean
	private CaseMessageRepository caseMessages;
	// Stores found-item records.
	@MockitoBean
	private FoundItemRepository foundItems;
	// Stores lost-item reports.
	@MockitoBean
	private LostReportRepository lostReports;
	// Stores ownership claims on found items.
	@MockitoBean
	private ClaimRepository claims;
	// Stores in-app notifications.
	@MockitoBean
	private NotificationRepository notifications;
	// Stores per-channel notification delivery records.
	@MockitoBean
	private NotificationDeliveryRepository notificationDeliveries;
	// Stores audit-trail entries.
	@MockitoBean
	private AuditLogRepository auditLogs;
	// Stores application user accounts.
	@MockitoBean
	private AppUserRepository users;
	// Stores campus zone definitions.
	@MockitoBean
	private CampusZoneRepository campusZones;
	// Stores event recovery hub configurations.
	@MockitoBean
	private EventRecoveryHubRepository eventHubs;
	// Stores asset registry records.
	@MockitoBean
	private AssetRegistryRecordRepository assetRecords;
	// Stores recovery case aggregates.
	@MockitoBean
	private RecoveryCaseRepository recoveryCases;
	// Stores chain-of-custody events.
	@MockitoBean
	private CustodyEventRepository custodyEvents;
	// Stores generated return passes.
	@MockitoBean
	private ReturnPassRepository returnPasses;
	// Stores prevention alerts.
	@MockitoBean
	private PreventionAlertRepository preventionAlerts;
	// Stores recovery node definitions.
	@MockitoBean
	private RecoveryNodeRepository recoveryNodes;
	// Stores users' saved searches.
	@MockitoBean
	private SavedSearchRepository savedSearches;
	// Stores users' watched items.
	@MockitoBean
	private WatchedItemRepository watchedItems;
	// Stores support tickets.
	@MockitoBean
	private SupportTicketRepository supportTickets;
	// Stores global system settings.
	@MockitoBean
	private SystemSettingRepository systemSettings;

	/**
	 * Scenario: the application context starts with all collaborators mocked.
	 * Act: Spring builds the entire bean graph during test setup.
	 * Assert: the test body is intentionally empty — reaching it at all proves the context loaded
	 * without any wiring/configuration failures (no assertion needed; a failed context fails the test).
	 */
	@Test
	void contextLoads() {
	}

}
