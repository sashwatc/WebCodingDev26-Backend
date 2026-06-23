package com.FBLA.WebCodingDev26Backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.FBLA.WebCodingDev26Backend.config.JacksonConfig;
import com.FBLA.WebCodingDev26Backend.dto.CustodyVerificationResponse;
import com.FBLA.WebCodingDev26Backend.dto.DemoScenarioResponse;
import com.FBLA.WebCodingDev26Backend.dto.PatternReviewResult;
import com.FBLA.WebCodingDev26Backend.dto.PartnerRelayResponse;
import com.FBLA.WebCodingDev26Backend.dto.PublicFoundItemResponse;
import com.FBLA.WebCodingDev26Backend.dto.RecoveryCenterResponse;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRedeemRequest;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRequest;
import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.ConflictException;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.CustodyEvent;
import com.FBLA.WebCodingDev26Backend.model.EventRecoveryHub;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.PartnerRelay;
import com.FBLA.WebCodingDev26Backend.model.PreventionAlert;
import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.model.RecoveryMission;
import com.FBLA.WebCodingDev26Backend.model.ReturnPass;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.CampusZoneRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.CustodyEventRepository;
import com.FBLA.WebCodingDev26Backend.repository.EventRecoveryHubRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import com.FBLA.WebCodingDev26Backend.repository.PartnerRelayRepository;
import com.FBLA.WebCodingDev26Backend.repository.PreventionAlertRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryCaseRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryMissionRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryNodeRepository;
import com.FBLA.WebCodingDev26Backend.repository.ReturnPassRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecoveryMeshRulesTest {
    @Mock
    private LostReportRepository lostReports;
    @Mock
    private FoundItemRepository foundItems;
    @Mock
    private ClaimRepository claims;
    @Mock
    private NotificationRepository notifications;
    @Mock
    private AuditLogRepository auditLogs;
    @Mock
    private RecoveryCaseRepository recoveryCases;
    @Mock
    private RecoveryMissionRepository recoveryMissions;
    @Mock
    private CampusZoneRepository campusZones;
    @Mock
    private CustodyEventRepository custodyEvents;
    @Mock
    private ReturnPassRepository returnPasses;
    @Mock
    private PreventionAlertRepository preventionAlerts;
    @Mock
    private EventRecoveryHubRepository eventHubs;
    @Mock
    private PartnerRelayRepository partnerRelays;
    @Mock
    private RecoveryNodeRepository recoveryNodes;

    @Test
    void publicFoundItemDtoExcludesProofVaultAndPrivateFields() throws Exception {
        FoundItem item = foundItem("found_private", "approved");
        item.setPrivateVerificationClues(List.of("engraving under case"));
        item.setStorageLocation("Main Office shelf A");
        item.setFinderEmail("finder@pleasantvalley.edu");
        item.setDepartmentDestination("Technology Office");

        ObjectMapper mapper = new JacksonConfig().objectMapper();
        String json = mapper.writeValueAsString(PublicFoundItemResponse.from(item));

        assertThat(json).doesNotContain("private_verification_clues", "storage_location", "finder_email", "department_destination");
    }

    @Test
    void restrictedAssetItemsDoNotAppearInPublicList() {
        FoundItem publicItem = foundItem("found_public", "approved");
        FoundItem restricted = foundItem("found_asset", "approved");
        restricted.setRestrictedVisibility(true);

        when(foundItems.findAll()).thenReturn(List.of(publicItem, restricted));

        FoundItemService service = new FoundItemService(foundItems, mapper(), clock(), null, null, null, null, null, null, null);

        assertThat(service.list()).extracting(PublicFoundItemResponse::id).containsExactly("found_public");
    }

    @Test
    void onlyApprovedAvailableItemsAppearInPublicList() {
        FoundItem approved = foundItem("found_approved", "approved");
        FoundItem pending = foundItem("found_pending", "pending_review");
        FoundItem claimed = foundItem("found_claimed", "claimed");
        FoundItem returned = foundItem("found_returned", "returned");

        when(foundItems.findAll()).thenReturn(List.of(approved, pending, claimed, returned));

        FoundItemService service = new FoundItemService(foundItems, mapper(), clock(), null, null, null, null, null, null, null);

        assertThat(service.list()).extracting(PublicFoundItemResponse::id).containsExactly("found_approved");
    }

    @Test
    void lostReportCreationDoesNotCreateFoundItemAndEnsuresOneRecoveryCase() {
        GenericEntityService generic = new GenericEntityService(lostReports, claims, notifications, auditLogs, mapper(), clock(), null, foundItems, null, null);

        when(lostReports.save(any(LostReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LostReport report = (LostReport) generic.create("LostReport", Map.of(
                "title", "Lost AirPods",
                "category", "electronics",
                "contact_email", "mia@pleasantvalley.edu"
        ));

        assertThat(report.getId()).startsWith("lost_");
        verify(foundItems, never()).save(any());
    }

    @Test
    void multipleApprovedOrCompletedClaimsAreBlocked() {
        FoundItem item = foundItem("found_001", "claimed");
        Claim existing = claim("claim_existing", "found_001", "approved");

        when(foundItems.existsById("found_001")).thenReturn(true);
        when(claims.findByFoundItemId("found_001")).thenReturn(List.of(existing));

        GenericEntityService generic = new GenericEntityService(lostReports, claims, notifications, auditLogs, mapper(), clock(), null, foundItems, null, null);

        assertThatThrownBy(() -> generic.create("Claim", Map.of(
                "id", "claim_new",
                "found_item_id", item.getId(),
                "claimant_email", "other@pleasantvalley.edu",
                "status", "approved"
        ))).isInstanceOf(ConflictException.class);
    }

    @Test
    void returnPassRequiresApprovedClaimAndCannotRedeemTwiceOrExpired() {
        Claim pending = claim("claim_pending", "found_001", "pending_review");
        when(claims.findById("claim_pending")).thenReturn(Optional.of(pending));

        ReturnPassService service = returnPassService();

        assertThatThrownBy(() -> service.create("claim_pending", new ReturnPassRequest("", ""), admin()))
                .isInstanceOf(ConflictException.class);

        Claim approved = claim("claim_approved", "found_001", "approved");
        FoundItem claimed = foundItem("found_001", "claimed");
        ReturnPass active = pass("pass_active", "claim_approved", "found_001", "active", "123456", "2026-06-24T12:00:00Z");

        when(claims.findById("claim_approved")).thenReturn(Optional.of(approved));
        when(foundItems.findById("found_001")).thenReturn(Optional.of(claimed));
        when(returnPasses.findById("pass_active")).thenReturn(Optional.of(active));
        when(returnPasses.save(any(ReturnPass.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claims.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(foundItems.save(any(FoundItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(custodyEvents.findByFoundItemIdOrderBySequenceNumberAsc("found_001")).thenReturn(new ArrayList<>());
        when(custodyEvents.save(any(CustodyEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.redeem("pass_active", new ReturnPassRedeemRequest("123456"), admin());
        assertThatThrownBy(() -> service.redeem("pass_active", new ReturnPassRedeemRequest("123456"), admin()))
                .isInstanceOf(ConflictException.class);

        ReturnPass expired = pass("pass_expired", "claim_approved", "found_001", "active", "999999", "2026-06-20T12:00:00Z");
        when(returnPasses.findById("pass_expired")).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.redeem("pass_expired", new ReturnPassRedeemRequest("999999"), admin()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void custodyChainVerifiesAndDetectsAlteredData() {
        CustodyLedgerService service = new CustodyLedgerService(custodyEvents, clock());
        CustodyEvent event = custodyEvent("found_001", 1, "intake_created", "");
        event.setEventHash(service.hash(event));

        when(custodyEvents.findByFoundItemIdOrderBySequenceNumberAsc("found_001")).thenReturn(List.of(event));

        CustodyVerificationResponse verified = service.verify("found_001");
        assertThat(verified.verified()).isTrue();

        event.setNotes("altered after hash");
        CustodyVerificationResponse altered = service.verify("found_001");
        assertThat(altered.verified()).isFalse();
        assertThat(altered.issues()).anyMatch(issue -> issue.contains("Altered event data"));
    }

    @Test
    void displayFeedExcludesRestrictedItems() {
        EventRecoveryHub hub = new EventRecoveryHub();
        hub.setId("hub_001");
        hub.setPublicEnabled(true);
        hub.setDisplayEnabled(true);
        hub.setCampusZoneIds(List.of());

        FoundItem visible = foundItem("found_visible", "approved");
        visible.setEventHubId("hub_001");
        FoundItem restricted = foundItem("found_restricted", "approved");
        restricted.setRestrictedVisibility(true);
        restricted.setEventHubId("hub_001");

        when(eventHubs.findById("hub_001")).thenReturn(Optional.of(hub));
        when(foundItems.findByEventHubId("hub_001")).thenReturn(List.of(visible, restricted));
        when(campusZones.findAllById(List.of())).thenReturn(List.of());

        FoundItemService itemService = new FoundItemService(foundItems, mapper(), clock(), null, null, null, null, null, null, null);
        EventRecoveryService service = new EventRecoveryService(campusZones, eventHubs, foundItems, itemService, mapper(), clock());

        Map<String, Object> feed = service.displayFeed("hub_001");

        @SuppressWarnings("unchecked")
        List<PublicFoundItemResponse> items = (List<PublicFoundItemResponse>) feed.get("found_items");
        assertThat(items).extracting(PublicFoundItemResponse::id).containsExactly("found_visible");
    }

    @Test
    void sentinelReturnsNotEnoughDataInsteadOfFabricatingAlert() {
        LossSentinelService service = new LossSentinelService(preventionAlerts, lostReports, campusZones, mapper(), clock());

        when(lostReports.findAll()).thenReturn(List.of(
                lost("lost_1", "zone_gym", "electronics", "2026-06-21"),
                lost("lost_2", "zone_gym", "electronics", "2026-06-21"),
                lost("lost_3", "zone_gym", "electronics", "2026-06-21")
        ));
        when(preventionAlerts.findAll()).thenReturn(List.of());
        PatternReviewResult result = service.recompute();

        assertThat(result.state()).isEqualTo("not_enough_data");
        assertThat(result.alerts()).isEmpty();
        verify(preventionAlerts, never()).save(any(PreventionAlert.class));
    }

    @Test
    void sentinelUsesRealLostReportSourcesAndDetectsSpikeWithBaseline() {
        LossSentinelService service = new LossSentinelService(preventionAlerts, lostReports, campusZones, mapper(), clock());

        when(lostReports.findAll()).thenReturn(List.of(
                lost("lost_base_1", "zone_gym", "electronics", "2026-05-20"),
                lost("lost_base_2", "zone_gym", "electronics", "2026-06-01"),
                lost("lost_1", "zone_gym", "electronics", "2026-06-18"),
                lost("lost_2", "zone_gym", "electronics", "2026-06-19"),
                lost("lost_3", "zone_gym", "electronics", "2026-06-20"),
                lost("lost_4", "zone_gym", "electronics", "2026-06-21")
        ));
        when(preventionAlerts.findAll()).thenReturn(List.of());
        when(preventionAlerts.save(any(PreventionAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatternReviewResult result = service.recompute();

        assertThat(result.state()).isEqualTo("alerts_created");
        assertThat(result.alerts()).hasSize(1);
        PreventionAlert alert = result.alerts().get(0);
        assertThat(alert.getObservedCount()).isEqualTo(4);
        assertThat(alert.getBaselineCount()).isEqualTo(1);
        assertThat(alert.getSourceLostReportIds()).containsExactly("lost_1", "lost_2", "lost_3", "lost_4");
        assertThat(alert.getBaselineWindowStart()).isEqualTo("2026-05-16");
        assertThat(alert.getBaselineWindowEnd()).isEqualTo("2026-06-14");
        assertThat(alert.getCalculatedAt()).isEqualTo("2026-06-22T12:00:00Z");
        verify(preventionAlerts).save(alert);
    }

    @Test
    void sentinelRecomputeUpdatesExistingActiveAlertInsteadOfDuplicating() {
        LossSentinelService service = new LossSentinelService(preventionAlerts, lostReports, campusZones, mapper(), clock());
        PreventionAlert existing = new PreventionAlert();
        existing.setId("alert_existing");
        existing.setTenantId("pvhs");
        existing.setAlertType("volume_spike");
        existing.setCampusZoneId("zone_gym");
        existing.setCategory("electronics");
        existing.setTimeWindowStart("2026-06-15");
        existing.setTimeWindowEnd("2026-06-22");
        existing.setStatus("open");
        existing.setCreatedDate("2026-06-22T09:00:00Z");

        when(preventionAlerts.findAll()).thenReturn(List.of(existing));
        when(lostReports.findAll()).thenReturn(List.of(
                lost("lost_base_1", "zone_gym", "electronics", "2026-05-20"),
                lost("lost_base_2", "zone_gym", "electronics", "2026-06-01"),
                lost("lost_1", "zone_gym", "electronics", "2026-06-18"),
                lost("lost_2", "zone_gym", "electronics", "2026-06-19"),
                lost("lost_3", "zone_gym", "electronics", "2026-06-20"),
                lost("lost_4", "zone_gym", "electronics", "2026-06-21")
        ));
        when(preventionAlerts.save(any(PreventionAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recompute();

        verify(preventionAlerts).save(existing);
        assertThat(existing.getId()).isEqualTo("alert_existing");
        assertThat(existing.getObservedCount()).isEqualTo(4);
        assertThat(existing.getSourceLostReportIds()).containsExactly("lost_1", "lost_2", "lost_3", "lost_4");
        assertThat(existing.getCreatedDate()).isEqualTo("2026-06-22T09:00:00Z");
    }

    @Test
    void sentinelAlertCanExposeSourceReportsAndCreateRecoveryMission() {
        RecoveryCaseService recoveryCaseWorkflow = org.mockito.Mockito.mock(RecoveryCaseService.class);
        LossSentinelService service = new LossSentinelService(
                preventionAlerts,
                lostReports,
                campusZones,
                recoveryCaseWorkflow,
                auditLogs,
                mapper(),
                clock(),
                new InputSanitizer()
        );
        PreventionAlert alert = new PreventionAlert();
        alert.setId("alert_gym");
        alert.setCategory("electronics");
        alert.setCampusZoneId("zone_gym");
        alert.setSeverity("high");
        alert.setSourceLostReportIds(List.of("lost_1"));
        alert.setSuggestedActions(List.of("Create recovery mission"));
        alert.setReasons(List.of("4 recent reports"));
        LostReport source = lost("lost_1", "zone_gym", "electronics", "2026-06-21");
        RecoveryCase recoveryCase = recoveryCase("case_1", "lost_1", "open");
        RecoveryMission mission = mission("mission_alert", "case_1", "zone_gym", "Gym", 90, "high", "open");

        when(preventionAlerts.findById("alert_gym")).thenReturn(Optional.of(alert));
        when(lostReports.findById("lost_1")).thenReturn(Optional.of(source));
        when(recoveryCaseWorkflow.ensureForLostReport(source)).thenReturn(recoveryCase);
        when(recoveryCaseWorkflow.createMission(any(), any(), any())).thenReturn(mission);
        when(auditLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.sourceReports("alert_gym")).extracting(LostReport::getId).containsExactly("lost_1");
        RecoveryMission created = service.createMissionFromAlert("alert_gym", "avery.patel@pleasantvalley.edu");

        assertThat(created.getId()).isEqualTo("mission_alert");
        verify(recoveryCaseWorkflow).createMission(any(), any(), any());
        verify(auditLogs).save(any());
    }

    @Test
    void recoveryCenterSummaryIsComputedFromStoredCasesMissionsAndClaims() {
        RecoveryCaseService service = new RecoveryCaseService(
                recoveryCases,
                recoveryMissions,
                lostReports,
                claims,
                auditLogs,
                new RecoveryPlanningService(campusZones, foundItems),
                mapper(),
                clock(),
                new InputSanitizer()
        );
        RecoveryCase open = recoveryCase("case_open", "lost_open", "open");
        RecoveryCase pickup = recoveryCase("case_pickup", "lost_pickup", "pickup_ready");
        RecoveryMission openMission = mission("mission_open", "case_open", "zone_gym", "Gym", 75, "high", "open");
        RecoveryMission completedMission = mission("mission_done", "case_open", "zone_gym", "Gym", 75, "high", "completed");
        Claim pendingClaim = claim("claim_pending_review", "found_001", "pending_review");
        Claim rejectedClaim = claim("claim_rejected", "found_002", "rejected");

        when(recoveryCases.findAll()).thenReturn(List.of(open, pickup));
        when(recoveryMissions.findByRecoveryCaseId("case_open")).thenReturn(List.of(openMission, completedMission));
        when(recoveryMissions.findByRecoveryCaseId("case_pickup")).thenReturn(List.of());
        when(lostReports.findById("lost_open")).thenReturn(Optional.of(lost("lost_open", "zone_gym", "electronics", "2026-06-21")));
        when(lostReports.findById("lost_pickup")).thenReturn(Optional.of(lost("lost_pickup", "zone_library", "books", "2026-06-21")));
        when(claims.findAll()).thenReturn(List.of(pendingClaim, rejectedClaim));

        RecoveryCenterResponse response = service.center();

        assertThat(response.summary().activeCases()).isEqualTo(2);
        assertThat(response.summary().openMissions()).isEqualTo(1);
        assertThat(response.summary().claimsAwaitingReview()).isEqualTo(1);
        assertThat(response.summary().pickupReadyCases()).isEqualTo(1);
        assertThat(response.cases()).extracting(item -> item.recoveryCase().getId()).containsExactly("case_open", "case_pickup");
    }

    @Test
    void recoveryCaseCreationRequiresAndPersistsRealLostReport() {
        RecoveryCaseService service = new RecoveryCaseService(
                recoveryCases,
                recoveryMissions,
                lostReports,
                claims,
                auditLogs,
                new RecoveryPlanningService(campusZones, foundItems),
                mapper(),
                clock(),
                new InputSanitizer()
        );
        when(lostReports.save(any(LostReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recoveryCases.findByLostReportId(any())).thenReturn(Optional.empty());
        when(recoveryCases.save(any(RecoveryCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RecoveryCase recoveryCase = service.createFromLostReportData(Map.of(
                "title", "Lost calculator",
                "category", "electronics",
                "contact_email", "riley.chen@pleasantvalley.edu"
        ), "avery.patel@pleasantvalley.edu");

        ArgumentCaptor<LostReport> reportCaptor = ArgumentCaptor.forClass(LostReport.class);
        verify(lostReports).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getId()).startsWith("lost_");
        assertThat(recoveryCase.getLostReportId()).isEqualTo(reportCaptor.getValue().getId());
        assertThatThrownBy(() -> service.ensureForLostReport(new LostReport()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void demoScenarioCreatesRealDemoDomainRecordsWithoutWipingData() {
        RecoveryCaseService recoveryCaseWorkflow = org.mockito.Mockito.mock(RecoveryCaseService.class);
        LossSentinelService sentinel = org.mockito.Mockito.mock(LossSentinelService.class);
        DemoScenarioService service = new DemoScenarioService(
                lostReports,
                foundItems,
                claims,
                auditLogs,
                recoveryCaseWorkflow,
                sentinel,
                clock(),
                new InputSanitizer()
        );
        RecoveryCase recoveryCase = recoveryCase("case_demo", "lost_demo", "open");
        recoveryCase.setIsDemo(true);
        AtomicInteger missionCounter = new AtomicInteger();

        when(lostReports.save(any(LostReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(foundItems.save(any(FoundItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claims.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recoveryCaseWorkflow.ensureForLostReport(any(LostReport.class))).thenReturn(recoveryCase);
        when(recoveryCaseWorkflow.createMission(any(), any(), any())).thenAnswer(invocation -> {
            RecoveryMission mission = new RecoveryMission();
            mission.setId("mission_demo_" + missionCounter.incrementAndGet());
            mission.setRecoveryCaseId("case_demo");
            mission.setIsDemo(true);
            return mission;
        });
        when(recoveryCaseWorkflow.update(any(), any(), any())).thenReturn(recoveryCase);

        DemoScenarioResponse response = service.create("airpods_gym", Map.of(), "avery.patel@pleasantvalley.edu");

        assertThat(response.lostReportIds()).hasSize(1);
        assertThat(response.recoveryCaseIds()).containsExactly("case_demo");
        assertThat(response.recoveryMissionIds()).containsExactly("mission_demo_1", "mission_demo_2");
        assertThat(response.foundItemIds()).hasSize(1);
        assertThat(response.claimIds()).hasSize(1);

        ArgumentCaptor<LostReport> lostCaptor = ArgumentCaptor.forClass(LostReport.class);
        ArgumentCaptor<FoundItem> foundCaptor = ArgumentCaptor.forClass(FoundItem.class);
        ArgumentCaptor<Claim> claimCaptor = ArgumentCaptor.forClass(Claim.class);
        verify(lostReports).save(lostCaptor.capture());
        verify(foundItems, atLeastOnce()).save(foundCaptor.capture());
        verify(claims).save(claimCaptor.capture());
        assertThat(lostCaptor.getValue().getIsDemo()).isTrue();
        assertThat(foundCaptor.getValue().getIsDemo()).isTrue();
        assertThat(claimCaptor.getValue().getIsDemo()).isTrue();
        verify(foundItems, never()).deleteById(any());
        verify(lostReports, never()).deleteById(any());
    }

    @Test
    void partnerRelayResponseStaysRedacted() {
        PartnerRelay relay = new PartnerRelay();
        relay.setId("relay_001");
        relay.setFoundItemId("found_private");
        relay.setPublicSummary("A possible match may be available at a partner location. Submit ownership evidence to continue.");
        relay.setRedactedMatchReasons(List.of("Category overlap"));
        relay.setStatus("suggested");
        when(partnerRelays.findAll()).thenReturn(List.of(relay));

        PartnerRelayService service = new PartnerRelayService(recoveryNodes, partnerRelays);

        List<PartnerRelayResponse> relays = service.relays();
        assertThat(relays).hasSize(1);
        assertThat(relays.get(0).publicSummary()).contains("possible match");
        assertThat(relays.get(0).toString()).doesNotContain("storage", "claimant", "private");
    }

    private ReturnPassService returnPassService() {
        return new ReturnPassService(returnPasses, claims, foundItems, notifications, new CustodyLedgerService(custodyEvents, clock()), new NoopRecoveryCaseService(), clock());
    }

    private PatchMapper mapper() {
        return new PatchMapper(new JacksonConfig().objectMapper());
    }

    private ClockService clock() {
        return new FixedClock();
    }

    private FoundItem foundItem(String id, String status) {
        FoundItem item = new FoundItem();
        item.setId(id);
        item.setTitle("Black AirPods-style Case");
        item.setCategory("electronics");
        item.setStatus(status);
        item.setRestrictedVisibility(false);
        item.setPhotoUrls(new ArrayList<>());
        item.setTags(new ArrayList<>());
        return item;
    }

    private Claim claim(String id, String foundItemId, String status) {
        Claim claim = new Claim();
        claim.setId(id);
        claim.setFoundItemId(foundItemId);
        claim.setClaimantEmail("student@pleasantvalley.edu");
        claim.setStatus(status);
        return claim;
    }

    private RecoveryCase recoveryCase(String id, String lostReportId, String status) {
        RecoveryCase recoveryCase = new RecoveryCase();
        recoveryCase.setId(id);
        recoveryCase.setLostReportId(lostReportId);
        recoveryCase.setStatus(status);
        recoveryCase.setCaseCode("PVHS-RM-" + id);
        recoveryCase.setCreatedDate("2026-06-22T12:00:00Z");
        recoveryCase.setUpdatedDate("2026-06-22T12:00:00Z");
        return recoveryCase;
    }

    private RecoveryMission mission(String id, String caseId, String zoneId, String zoneLabel, int score, String priority, String status) {
        RecoveryMission mission = new RecoveryMission();
        mission.setId(id);
        mission.setRecoveryCaseId(caseId);
        mission.setCampusZoneId(zoneId);
        mission.setZoneLabel(zoneLabel);
        mission.setScore(score);
        mission.setPriority(priority);
        mission.setStatus(status);
        mission.setCreatedDate("2026-06-22T12:00:00Z");
        mission.setUpdatedDate("2026-06-22T12:00:00Z");
        return mission;
    }

    private ReturnPass pass(String id, String claimId, String foundItemId, String status, String code, String expiresAt) {
        ReturnPass pass = new ReturnPass();
        pass.setId(id);
        pass.setClaimId(claimId);
        pass.setFoundItemId(foundItemId);
        pass.setClaimantEmail("student@pleasantvalley.edu");
        pass.setPickupLocation("PVHS Main Office");
        pass.setStatus(status);
        pass.setOneTimeCode(code);
        pass.setExpiresAt(expiresAt);
        return pass;
    }

    private CustodyEvent custodyEvent(String itemId, int sequence, String type, String previousHash) {
        CustodyEvent event = new CustodyEvent();
        event.setId("custody_" + sequence);
        event.setFoundItemId(itemId);
        event.setSequenceNumber(sequence);
        event.setEventType(type);
        event.setActorEmail("system@pvhs.demo");
        event.setActorRole("system");
        event.setLocation("");
        event.setNotes("seed");
        event.setPhotoEvidenceUrl("");
        event.setPreviousEventHash(previousHash);
        event.setCreatedDate("2026-06-22T12:00:00Z");
        return event;
    }

    private LostReport lost(String id, String zone, String category, String date) {
        LostReport report = new LostReport();
        report.setId(id);
        report.setCampusZoneId(zone);
        report.setCategory(category);
        report.setDateLost(date);
        return report;
    }

    private com.FBLA.WebCodingDev26Backend.model.AppUser admin() {
        com.FBLA.WebCodingDev26Backend.model.AppUser admin = new com.FBLA.WebCodingDev26Backend.model.AppUser();
        admin.setEmail("avery.patel@pleasantvalley.edu");
        admin.setRole("admin");
        return admin;
    }

    private static class FixedClock extends ClockService {
        @Override
        public String now() {
            return "2026-06-22T12:00:00Z";
        }
    }

    private class NoopRecoveryCaseService extends RecoveryCaseService {
        NoopRecoveryCaseService() {
            super(recoveryCases, recoveryMissions, lostReports, new RecoveryPlanningService(campusZones, foundItems), mapper(), clock());
        }

        @Override
        public void markPickupReady(String claimId, String foundItemId) {
        }

        @Override
        public void markReturned(String claimId, String foundItemId) {
        }
    }
}
