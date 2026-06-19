package com.FBLA.WebCodingDev26Backend;

import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
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
	private AuditLogRepository auditLogs;
	@MockitoBean
	private AppUserRepository users;

	@Test
	void contextLoads() {
	}

}
