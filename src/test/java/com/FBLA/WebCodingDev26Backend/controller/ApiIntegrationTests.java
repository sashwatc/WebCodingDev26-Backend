package com.FBLA.WebCodingDev26Backend.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
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
import com.FBLA.WebCodingDev26Backend.service.AuthService;
import com.FBLA.WebCodingDev26Backend.service.FoundItemService;
import com.FBLA.WebCodingDev26Backend.service.GenericEntityService;
import com.FBLA.WebCodingDev26Backend.service.HealthService;
import com.FBLA.WebCodingDev26Backend.service.MatchmakingService;
import com.FBLA.WebCodingDev26Backend.service.UploadService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new HealthController(healthService),
                        new FoundItemController(foundItemService),
                        new GenericEntityController(genericEntityService),
                        new MatchmakingController(matchmakingService),
                        new AuthController(authService),
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
        when(foundItemService.list()).thenReturn(List.of(foundItem("found_001"), foundItem("found_002")));
        doReturn(List.of(lostReport("lost_001"))).when(genericEntityService).list("LostReport");
        doReturn(List.of(claim("claim_001"))).when(genericEntityService).list("Claim");
        doReturn(List.of(notification("notif_001"))).when(genericEntityService).list("Notification");
        doReturn(List.of(auditLog("audit_001"))).when(genericEntityService).list("AuditLog");

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'found_001')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'found_002')]").exists());

        mockMvc.perform(get("/api/entities/LostReport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'lost_001')]").exists());

        mockMvc.perform(get("/api/entities/Claim"))
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

        when(foundItemService.list()).thenReturn(List.of(foundItem("found_001"), foundItem("found_002")));
        when(foundItemService.create(any())).thenReturn(testItem);
        when(foundItemService.update(eq("found_test"), any()))
                .thenReturn(approvedItem)
                .thenReturn(ratedItem);
        when(foundItemService.delete("found_test")).thenReturn(true);

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
    void genericEntityRoutesWorkForEverySupportedEntity() throws Exception {
        assertGenericEntityCrud("LostReport", "lost_test", lostReport("lost_test"), "{\"status\":\"matched\"}", "$.status", "matched");
        assertGenericEntityCrud("Claim", "claim_test", claim("claim_test"), "{\"status\":\"approved\"}", "$.status", "approved");
        assertGenericEntityCrud("Notification", "notif_test", notification("notif_test"), "{\"is_read\":true}", "$.is_read", true);
        assertGenericEntityCrud("AuditLog", "audit_test", auditLog("audit_test"), "{\"details\":\"Updated by test\"}", "$.details", "Updated by test");
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
                        .param("email", "riley.chen@pleasantvalley.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value("Riley C."));

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"full_name\":\"Avery Patel\",\"email\":\"avery.patel@pleasantvalley.edu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"));

        mockMvc.perform(get("/api/auth/user")
                        .param("email", "missing@pleasantvalley.edu"))
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
        when(genericEntityService.create(eq(entityName), any())).thenReturn(entity);
        doReturn(List.of(entity)).when(genericEntityService).list(entityName);
        when(genericEntityService.update(eq(entityName), eq(id), any())).thenReturn(patched);
        when(genericEntityService.delete(entityName, id)).thenReturn(true);

        mockMvc.perform(post("/api/entities/" + entityName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new JacksonConfig().objectMapper().writeValueAsString(entity)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(get("/api/entities/" + entityName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(patch("/api/entities/" + entityName + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath(patchedField).value(patchedValue));

        mockMvc.perform(delete("/api/entities/" + entityName + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
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
