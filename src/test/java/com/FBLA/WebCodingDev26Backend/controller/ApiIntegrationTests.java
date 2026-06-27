package com.FBLA.WebCodingDev26Backend.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.FBLA.WebCodingDev26Backend.config.JacksonConfig;
import com.FBLA.WebCodingDev26Backend.dto.HealthResponse;
import com.FBLA.WebCodingDev26Backend.dto.PublicFoundItemResponse;
import com.FBLA.WebCodingDev26Backend.dto.SignInRequest;
import com.FBLA.WebCodingDev26Backend.dto.UploadRequest;
import com.FBLA.WebCodingDev26Backend.dto.UploadResponse;
import com.FBLA.WebCodingDev26Backend.exception.GlobalExceptionHandler;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.MatchSuggestion;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.model.Rating;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.service.AiAssistanceService;
import com.FBLA.WebCodingDev26Backend.service.AuthService;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.FoundItemService;
import com.FBLA.WebCodingDev26Backend.service.GenericEntityService;
import com.FBLA.WebCodingDev26Backend.service.HealthService;
import com.FBLA.WebCodingDev26Backend.service.MatchmakingService;
import com.FBLA.WebCodingDev26Backend.service.UploadService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ApiIntegrationTests {
    @Mock
    private HealthService healthService;
    @Mock
    private FoundItemService foundItemService;
    @Mock
    private GenericEntityService genericEntityService;
    @Mock
    private AuthService authService;
    @Mock
    private UploadService uploadService;
    @Mock
    private MatchmakingService matchmakingService;
    @Mock
    private DemoAuthorizationService authorizationService;
    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private AiAssistanceService aiAssistanceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new HealthController(healthService),
                        new FoundItemController(foundItemService),
                        new AdminFoundItemController(foundItemService, authorizationService),
                        new GenericEntityController(genericEntityService, authorizationService),
                        new ClaimController(claimRepository, authorizationService),
                        new MatchmakingController(matchmakingService),
                        new AiAssistanceController(aiAssistanceService),
                        new AuthController(authService, authorizationService),
                        new UploadController(uploadService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        jsonConverter()
                )
                .addFilters(corsFilter())
                .build();
    }

    @Test
    void healthRouteReturnsMongoStatus() throws Exception {
        when(healthService.health()).thenReturn(new HealthResponse("ok", "mongodb", true));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.database").value("mongodb"))
                .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    void missingRouteReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/not-a-real-route"))
                .andExpect(status().isNotFound());
    }

    @Test
    void corsAllowsLocalFrontendOrigins() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://localhost:4173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4173"));
    }

    @Test
    void seedDataShapeIsAvailableThroughApiLists() throws Exception {
        List<PublicFoundItemResponse> seedItems = List.of(publicItem("found_001"), publicItem("found_002"));
        when(foundItemService.listFiltered(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Map.of("items", seedItems, "total", 2, "page", 0, "size", 20));
        doReturn(List.of(lostReport("lost_001"))).when(genericEntityService).list("LostReport");
        doReturn(List.of(claim("claim_001"))).when(genericEntityService).list("Claim");
        doReturn(List.of(notification("notif_001"))).when(genericEntityService).list("Notification");
        doReturn(List.of(auditLog("audit_001"))).when(genericEntityService).list("AuditLog");
        when(authorizationService.requireAdmin("avery.patel@pleasantvalley.edu"))
                .thenReturn(user("user_admin", "Avery Patel", "avery.patel@pleasantvalley.edu", "admin"));

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'found_001')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'found_002')]").exists());

        mockMvc.perform(get("/api/entities/LostReport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'lost_001')]").exists());

        mockMvc.perform(get("/api/entities/Claim")
                        .header("X-Demo-User-Email", "avery.patel@pleasantvalley.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'claim_001')]").exists());

        mockMvc.perform(get("/api/entities/Notification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'notif_001')]").exists());

        mockMvc.perform(get("/api/entities/AuditLog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'audit_001')]").exists());
    }

    @Test
    void itemRoutesCreatePatchListAndDelete() throws Exception {
        FoundItem testItem = foundItem("found_test");
        testItem.setTitle("Test Calculator");
        testItem.setLocationFound("Math Hall");
        testItem.setPhotoUrls(List.of("/items/test-calculator.jpg"));
        testItem.setTags(List.of("calculator"));

        FoundItem approvedItem = foundItem("found_test");
        approvedItem.setStatus("approved");
        approvedItem.setPhotoUrls(List.of("/items/test-calculator.jpg"));
        approvedItem.setTags(List.of("calculator"));

        FoundItem ratedItem = foundItem("found_test");
        Rating rating = new Rating();
        rating.setClaimId("claim_rating_test");
        rating.setRating(5);
        ratedItem.setRatings(new ArrayList<>(List.of(rating)));

        List<PublicFoundItemResponse> itemList = List.of(publicItem("found_001"), publicItem("found_002"));
        when(foundItemService.listFiltered(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Map.of("items", itemList, "total", 2, "page", 0, "size", 20));
        when(foundItemService.create(any(), anyBoolean())).thenReturn(testItem);
        when(foundItemService.update(eq("found_test"), any(), anyBoolean()))
                .thenReturn(approvedItem)
                .thenReturn(ratedItem);
        when(foundItemService.delete("found_test")).thenReturn(Map.of("success", true, "archived", false));

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "found_test",
                                  "title": "Test Calculator",
                                  "description": "Silver graphing calculator",
                                  "category": "school_supplies",
                                  "location_found": "Math Hall",
                                  "date_found": "2026-03-15",
                                  "photo_urls": ["/items/test-calculator.jpg"],
                                  "tags": ["calculator"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("found_test"))
                .andExpect(jsonPath("$.location_found").value("Math Hall"));

        mockMvc.perform(patch("/api/items/found_test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.photo_urls[0]").value("/items/test-calculator.jpg"))
                .andExpect(jsonPath("$.tags[0]").value("calculator"));

        mockMvc.perform(patch("/api/items/found_test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "upsert_rating": {
                                    "claim_id": "claim_rating_test",
                                    "rating": 5
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratings[0].claim_id").value("claim_rating_test"))
                .andExpect(jsonPath("$.ratings[0].rating").value(5));

        mockMvc.perform(delete("/api/items/found_test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void adminItemRouteReturnsFullModerationRecords() throws Exception {
        FoundItem adminItem = foundItem("found_admin");
        adminItem.setStorageLocation("Main Office shelf A");

        when(authorizationService.requireStaffOrAdmin("avery.patel@pleasantvalley.edu"))
                .thenReturn(user("user_admin", "Avery Patel", "avery.patel@pleasantvalley.edu", "admin"));
        when(foundItemService.listAdmin()).thenReturn(List.of(adminItem));

        mockMvc.perform(get("/api/admin/items")
                        .header("X-Demo-User-Email", "avery.patel@pleasantvalley.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("found_admin"))
                .andExpect(jsonPath("$[0].storage_location").value("Main Office shelf A"));
    }

    @Test
    void genericEntityRoutesWorkForEverySupportedEntity() throws Exception {
        assertGenericEntityCrud("LostReport", "lost_test", lostReport("lost_test"), "{\"status\":\"matched\"}", "$.status", "matched");
        assertGenericEntityCrud("Claim", "claim_test", claim("claim_test"), "{\"status\":\"approved\"}", "$.status", "approved");
        assertGenericEntityCrud("Notification", "notif_test", notification("notif_test"), "{\"is_read\":true}", "$.is_read", true);
        assertGenericEntityCrud("AuditLog", "audit_test", auditLog("audit_test"), "{\"details\":\"Updated by test\"}", "$.details", "Updated by test");
    }

    @Test
    void genericClaimReadAndPrivilegedPatchRequireAdmin() throws Exception {
        when(authorizationService.requireAdmin(null))
                .thenThrow(new com.FBLA.WebCodingDev26Backend.exception.ForbiddenException("Admin access is required."));

        mockMvc.perform(get("/api/entities/Claim"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/entities/Claim/claim_test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"completed\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void claimantPendingReviewFeedbackDoesNotRequireAdmin() throws Exception {
        Claim reviewed = claim("claim_review");
        reviewed.setReviewStatus("pending");
        when(genericEntityService.update(eq("Claim"), eq("claim_review"), any())).thenReturn(reviewed);

        mockMvc.perform(patch("/api/entities/Claim/claim_review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimant_rating\":5,\"claimant_review\":\"Helpful pickup.\",\"review_status\":\"pending\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review_status").value("pending"));
    }

    @Test
    void claimMineReturnsOnlyResolvedUserClaims() throws Exception {
        Claim ownClaim = claim("claim_own");
        ownClaim.setClaimantEmail("jordan.kim@pleasantvalley.edu");
        when(authorizationService.resolveEmail("jordan.kim@pleasantvalley.edu"))
                .thenReturn("jordan.kim@pleasantvalley.edu");
        when(claimRepository.findByClaimantEmail("jordan.kim@pleasantvalley.edu"))
                .thenReturn(List.of(ownClaim));

        mockMvc.perform(get("/api/claims/mine")
                        .header("X-Demo-User-Email", "jordan.kim@pleasantvalley.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("claim_own"))
                .andExpect(jsonPath("$[0].claimant_email").value("jordan.kim@pleasantvalley.edu"));
    }

    @Test
    void aiAssistanceRoutesReturnEditableSuggestions() throws Exception {
        when(aiAssistanceService.parseSearchQuery(any())).thenReturn(Map.of(
                "source", "deterministic",
                "editable", true,
                "category", "electronics"
        ));
        when(aiAssistanceService.suggestFoundItemFields(any())).thenReturn(Map.of(
                "source", "deterministic",
                "editable", true,
                "tags", List.of("airpods")
        ));

        mockMvc.perform(post("/api/ai-assistance/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"black earbuds near gym\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.source").value("deterministic"));

        mockMvc.perform(post("/api/ai-assistance/found-item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"AirPods case\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value("airpods"));
    }

    @Test
    void matchmakingRoutesFetchAndRefreshSuggestions() throws Exception {
        MatchSuggestion suggestion = matchSuggestion();

        when(matchmakingService.getMatchesForLostReport("lost_001")).thenReturn(List.of(suggestion));
        when(matchmakingService.refreshMatchesForLostReport("lost_001")).thenReturn(List.of(suggestion));
        when(matchmakingService.refreshMatchesForFoundItem("found_002")).thenReturn(List.of(suggestion));

        mockMvc.perform(get("/api/matches/lost-reports/lost_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].found_item_id").value("found_002"))
                .andExpect(jsonPath("$[0].confidence").value(96))
                .andExpect(jsonPath("$[0].source").value("ai"));

        mockMvc.perform(post("/api/matches/lost-reports/lost_001/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].found_item_title").value("Blue JanSport Backpack"));

        mockMvc.perform(post("/api/matches/found-items/found_002/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reasons[0]").value("brand match"));
    }

    @Test
    void authRoutesUpsertAndFetchUser() throws Exception {
        AppUser student = user("user_riley", "Riley Chen", "riley.chen@pleasantvalley.edu", "student");
        AppUser updatedStudent = user("user_riley", "Riley C.", "riley.chen@pleasantvalley.edu", "student");
        AppUser admin = user("user_admin", "Avery Patel", "avery.patel@pleasantvalley.edu", "admin");

        when(authService.signIn(any(SignInRequest.class))).thenReturn(student, updatedStudent, admin);
        when(authService.findByEmail("riley.chen@pleasantvalley.edu")).thenReturn(Optional.of(updatedStudent));
        when(authService.findByEmail("missing@pleasantvalley.edu")).thenReturn(Optional.empty());
        // A user may look up their own account; the resolved caller must match the requested email.
        when(authorizationService.resolveEmail("riley.chen@pleasantvalley.edu")).thenReturn("riley.chen@pleasantvalley.edu");
        when(authorizationService.resolveEmail("missing@pleasantvalley.edu")).thenReturn("missing@pleasantvalley.edu");

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"full_name\":\"Riley Chen\",\"email\":\"RILEY.CHEN@pleasantvalley.edu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("riley.chen@pleasantvalley.edu"))
                .andExpect(jsonPath("$.role").value("student"));

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"full_name\":\"Riley C.\",\"email\":\"riley.chen@pleasantvalley.edu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value("Riley C."));

        mockMvc.perform(get("/api/auth/user")
                        .param("email", "riley.chen@pleasantvalley.edu")
                        .header("X-Demo-User-Email", "riley.chen@pleasantvalley.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value("Riley C."));

        // Looking up another user's account without staff/admin is now forbidden.
        mockMvc.perform(get("/api/auth/user")
                        .param("email", "avery.patel@pleasantvalley.edu")
                        .header("X-Demo-User-Email", "riley.chen@pleasantvalley.edu"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"full_name\":\"Avery Patel\",\"email\":\"avery.patel@pleasantvalley.edu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"));

        mockMvc.perform(get("/api/auth/user")
                        .param("email", "missing@pleasantvalley.edu")
                        .header("X-Demo-User-Email", "missing@pleasantvalley.edu"))
                .andExpect(status().isOk())
                .andExpect(content().string("null"));
    }

    @Test
    void uploadRouteReturnsDataUrl() throws Exception {
        when(uploadService.upload(any(UploadRequest.class))).thenReturn(new UploadResponse("data:text/plain;base64,dGVzdA=="));

        mockMvc.perform(post("/api/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file_name\":\"tiny.txt\",\"content_type\":\"text/plain\",\"data_url\":\"data:text/plain;base64,dGVzdA==\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file_url").value("data:text/plain;base64,dGVzdA=="));
    }

    private void assertGenericEntityCrud(String entityName, String id, Object entity, String patchBody, String patchedField, Object patchedValue) throws Exception {
        Object patched = patchedEntity(entity);
        boolean isClaim = "Claim".equals(entityName);
        if (isClaim) {
            when(authorizationService.requireAdmin("avery.patel@pleasantvalley.edu"))
                    .thenReturn(user("user_admin", "Avery Patel", "avery.patel@pleasantvalley.edu", "admin"));
        }
        when(genericEntityService.create(eq(entityName), any())).thenReturn(entity);
        doReturn(List.of(entity)).when(genericEntityService).list(entityName);
        when(genericEntityService.update(eq(entityName), eq(id), any())).thenReturn(patched);
        when(genericEntityService.delete(entityName, id)).thenReturn(true);

        mockMvc.perform(post("/api/entities/" + entityName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new JacksonConfig().objectMapper().writeValueAsString(entity)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(withAdminIfClaim(get("/api/entities/" + entityName), isClaim))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(withAdminIfClaim(patch("/api/entities/" + entityName + "/" + id), isClaim)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath(patchedField).value(patchedValue));

        mockMvc.perform(withAdminIfClaim(delete("/api/entities/" + entityName + "/" + id), isClaim))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withAdminIfClaim(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            boolean isClaim
    ) {
        return isClaim ? request.header("X-Demo-User-Email", "avery.patel@pleasantvalley.edu") : request;
    }

    private Object patchedEntity(Object entity) {
        if (entity instanceof LostReport lostReport) {
            lostReport.setStatus("matched");
            return lostReport;
        }
        if (entity instanceof Claim claim) {
            claim.setStatus("approved");
            return claim;
        }
        if (entity instanceof Notification notification) {
            notification.setIsRead(true);
            return notification;
        }
        if (entity instanceof AuditLog auditLog) {
            auditLog.setDetails("Updated by test");
            return auditLog;
        }
        return entity;
    }

    private CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:4173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return new CorsFilter(source);
    }

    private JacksonJsonHttpMessageConverter jsonConverter() {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        return new JacksonJsonHttpMessageConverter(mapper);
    }

    private FoundItem foundItem(String id) {
        FoundItem item = new FoundItem();
        item.setId(id);
        item.setTitle("Black Hydro Flask Water Bottle");
        item.setCategory("food_containers");
        item.setDescription("Matte black bottle.");
        item.setLocationFound("Gymnasium");
        item.setStatus("approved");
        item.setRecordType("found");
        item.setPhotoUrls(new ArrayList<>());
        item.setTags(new ArrayList<>());
        item.setRatings(new ArrayList<>());
        return item;
    }

    private PublicFoundItemResponse publicItem(String id) {
        return PublicFoundItemResponse.from(foundItem(id));
    }

    private LostReport lostReport(String id) {
        LostReport report = new LostReport();
        report.setId(id);
        report.setTitle("Lost Hoodie");
        report.setCategory("clothing");
        report.setContactEmail("jordan.kim@pleasantvalley.edu");
        report.setStatus("open");
        report.setMatchedItems(new ArrayList<>());
        report.setPhotoUrls(new ArrayList<>());
        return report;
    }

    private MatchSuggestion matchSuggestion() {
        MatchSuggestion suggestion = new MatchSuggestion();
        suggestion.setFoundItemId("found_002");
        suggestion.setFoundItemTitle("Blue JanSport Backpack");
        suggestion.setConfidence(96);
        suggestion.setReasons(List.of("brand match", "color match"));
        suggestion.setSource("ai");
        suggestion.setStatus("suggested");
        return suggestion;
    }

    private Claim claim(String id) {
        Claim claim = new Claim();
        claim.setId(id);
        claim.setFoundItemId("found_001");
        claim.setClaimantEmail("riley.chen@pleasantvalley.edu");
        claim.setClaimantName("Riley Chen");
        claim.setStatus("pending_review");
        claim.setRiskFlags(new ArrayList<>());
        return claim;
    }

    private Notification notification(String id) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUserEmail("riley.chen@pleasantvalley.edu");
        notification.setTitle("Claim update");
        notification.setMessage("Your claim was reviewed.");
        notification.setType("claim_update");
        notification.setIsRead(false);
        return notification;
    }

    private AuditLog auditLog(String id) {
        AuditLog auditLog = new AuditLog();
        auditLog.setId(id);
        auditLog.setAction("Integration test");
        auditLog.setEntityType("system");
        auditLog.setEntityId("test");
        auditLog.setPerformedBy("test");
        return auditLog;
    }

    private AppUser user(String id, String fullName, String email, String role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setAvatarUrl("");
        return user;
    }
}
