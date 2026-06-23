package com.FBLA.WebCodingDev26Backend;

import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import com.FBLA.WebCodingDev26Backend.repository.AssetRegistryRecordRepository;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.CampusZoneRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.CustodyEventRepository;
import com.FBLA.WebCodingDev26Backend.repository.EventRecoveryHubRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationDeliveryRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import com.FBLA.WebCodingDev26Backend.repository.PartnerRelayRepository;
import com.FBLA.WebCodingDev26Backend.repository.PreventionAlertRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryCaseRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryMissionRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryNodeRepository;
import com.FBLA.WebCodingDev26Backend.repository.ReturnPassRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
				+ "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration,"
				+ "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration",
		"app.seed.enabled=false"
})
class WebCodingDev26BackendApplicationTests {
	@MockitoBean
	private MongoTemplate mongoTemplate;
	@MockitoBean
	private FoundItemRepository foundItems;
	@MockitoBean
	private LostReportRepository lostReports;
	@MockitoBean
	private ClaimRepository claims;
	@MockitoBean
	private NotificationRepository notifications;
	@MockitoBean
	private NotificationDeliveryRepository notificationDeliveries;
	@MockitoBean
	private AuditLogRepository auditLogs;
	@MockitoBean
	private AppUserRepository users;
	@MockitoBean
	private CampusZoneRepository campusZones;
	@MockitoBean
	private EventRecoveryHubRepository eventHubs;
	@MockitoBean
	private AssetRegistryRecordRepository assetRecords;
	@MockitoBean
	private RecoveryCaseRepository recoveryCases;
	@MockitoBean
	private RecoveryMissionRepository recoveryMissions;
	@MockitoBean
	private CustodyEventRepository custodyEvents;
	@MockitoBean
	private ReturnPassRepository returnPasses;
	@MockitoBean
	private PreventionAlertRepository preventionAlerts;
	@MockitoBean
	private RecoveryNodeRepository recoveryNodes;
	@MockitoBean
	private PartnerRelayRepository partnerRelays;

	@Test
	void contextLoads() {
	}

}
