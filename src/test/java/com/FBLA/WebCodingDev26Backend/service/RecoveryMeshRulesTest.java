package com.FBLA.WebCodingDev26Backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.FBLA.WebCodingDev26Backend.config.JacksonConfig;
import com.FBLA.WebCodingDev26Backend.dto.CustodyVerificationResponse;
import com.FBLA.WebCodingDev26Backend.dto.PartnerRelayResponse;
import com.FBLA.WebCodingDev26Backend.dto.PublicFoundItemResponse;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRedeemRequest;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRequest;
import com.FBLA.WebCodingDev26Backend.exception.ConflictException;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.CustodyEvent;
import com.FBLA.WebCodingDev26Backend.model.EventRecoveryHub;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.PartnerRelay;
import com.FBLA.WebCodingDev26Backend.model.PreventionAlert;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    void sentinelIgnoresInsufficientSamplesAndDetectsSpike() {
        LossSentinelService service = new LossSentinelService(preventionAlerts, lostReports, campusZones, mapper(), clock());

        when(lostReports.findAll()).thenReturn(List.of(
                lost("lost_1", "zone_gym", "electronics", "2026-06-21"),
                lost("lost_2", "zone_gym", "electronics", "2026-06-21"),
                lost("lost_3", "zone_gym", "electronics", "2026-06-21")
        ));
        when(preventionAlerts.findAll()).thenReturn(List.of());
        service.recompute();
        verify(preventionAlerts, never()).save(any(PreventionAlert.class));

        when(lostReports.findAll()).thenReturn(List.of(
                lost("lost_1", "zone_gym", "electronics", "2026-06-21"),
                lost("lost_2", "zone_gym", "electronics", "2026-06-21"),
                lost("lost_3", "zone_gym", "electronics", "2026-06-21"),
                lost("lost_4", "zone_gym", "electronics", "2026-06-21")
        ));
        when(preventionAlerts.save(any(PreventionAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.recompute();
        verify(preventionAlerts).save(any(PreventionAlert.class));
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
                lost("lost_1", "zone_gym", "electronics", "2026-06-21"),
                lost("lost_2", "zone_gym", "electronics", "2026-06-21"),
                lost("lost_3", "zone_gym", "electronics", "2026-06-21"),
                lost("lost_4", "zone_gym", "electronics", "2026-06-21")
        ));
        when(preventionAlerts.save(any(PreventionAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recompute();

        verify(preventionAlerts).save(existing);
        assertThat(existing.getId()).isEqualTo("alert_existing");
        assertThat(existing.getObservedCount()).isEqualTo(4);
        assertThat(existing.getCreatedDate()).isEqualTo("2026-06-22T09:00:00Z");
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
