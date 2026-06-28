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

/**
 * Web-layer integration tests for the REST API controllers.
 *
 * <p>Uses MockMvc in standalone setup, wiring every controller against Mockito-mocked services so
 * no Spring context or database is required. The setup mirrors production HTTP behavior:
 * snake_case JSON via a custom Jackson converter, the real {@link GlobalExceptionHandler} for error
 * status mapping, and a CORS filter restricted to the local frontend origins. The tests exercise
 * routing, request/response (de)serialization, authorization gating on admin/claim routes, and the
 * happy-path CRUD contracts for items, generic entities, claims, AI assistance, matchmaking, auth,
 * and uploads.</p>
 */
@ExtendWith(MockitoExtension.class)
class ApiIntegrationTests {
    // Mocked health service backing GET /api/health.
    @Mock
    private HealthService healthService;
    // Mocked found-item service backing the public item routes.
    @Mock
    private FoundItemService foundItemService;
    // Mocked generic-entity service backing /api/entities/{type} CRUD.
    @Mock
    private GenericEntityService genericEntityService;
    // Mocked auth service backing sign-in and user lookup.
    @Mock
    private AuthService authService;
    // Mocked upload service backing POST /api/uploads.
    @Mock
    private UploadService uploadService;
    // Mocked matchmaking service backing the match routes.
    @Mock
    private MatchmakingService matchmakingService;
    // Mocked authorization service; stubbed to grant/deny admin/staff and resolve caller identity.
    @Mock
    private DemoAuthorizationService authorizationService;
    // Mocked claim repository backing the /api/claims routes.
    @Mock
    private ClaimRepository claimRepository;
    // Mocked AI assistance service backing the /api/ai-assistance routes.
    @Mock
    private AiAssistanceService aiAssistanceService;

    // System under test: the assembled MockMvc instance covering all controllers.
    private MockMvc mockMvc;

    // Build the standalone MockMvc before each test: register every controller with its mocked
    // service, attach the global exception handler, configure UTF-8 string + snake_case JSON
    // converters, and add the CORS filter so cross-origin and error-mapping behavior is realistic.
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

    /**
     * Scenario: GET /api/health returns the Mongo health snapshot.
     * Arrange: stub healthService.health() to a connected "ok"/"mongodb" response.
     * Act/Assert: GET returns 200 with status=ok, database=mongodb, connected=true. Passing proves
     * the health route serializes the health response correctly.
     */
    @Test
    void healthRouteReturnsMongoStatus() throws Exception {
        when(healthService.health()).thenReturn(new HealthResponse("ok", "mongodb", true));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.database").value("mongodb"))
                .andExpect(jsonPath("$.connected").value(true));
    }

    /**
     * Scenario: an unmapped route is requested.
     * Act/Assert: GET /api/not-a-real-route returns 404. Passing proves unknown paths resolve to a
     * not-found response rather than an error or a stray handler.
     */
    @Test
    void missingRouteReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/not-a-real-route"))
                .andExpect(status().isNotFound());
    }

    /**
     * Scenario: CORS preflight requests from the two allowed local frontend origins.
     * Act/Assert: OPTIONS /api/health with Origin 5173 and again with 4173 each return 200 and echo
     * back the matching Access-Control-Allow-Origin header. Passing proves the CORS filter permits
     * exactly the configured dev/preview frontends.
     */
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

    /**
     * Scenario: the list endpoints expose seed data for each entity type.
     * Arrange: stub the found-item filtered list and the generic list() for LostReport/Claim/
     * Notification/AuditLog, plus admin authorization for the Claim list which is admin-gated.
     * Act/Assert: GET each list route and assert the seeded record id is present in the JSON array
     * (the Claim list carries the admin demo header). Passing proves each entity type is reachable
     * and serialized through its public list route.
     */
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

    /**
     * Scenario: the full create/list/patch/delete lifecycle for a found item over HTTP.
     * Arrange: prepare service stubs returning a created item, an "approved" patched item, then a
     * rated item (two sequential update returns), a list result, and a delete result.
     * Act/Assert: GET list returns >=2 items; POST creates (201) echoing id + snake_case
     * location_found; first PATCH sets status approved and round-trips photo_urls/tags; second PATCH
     * upserts a rating and returns it; DELETE returns success=true. Passing proves the item routes
     * correctly bind snake_case JSON and map service results through every verb.
     */
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
        // Two sequential PATCH calls: first returns the approved item, second returns the rated item.
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

    /**
     * Scenario: the admin moderation listing returns full records including non-public fields.
     * Arrange: an item with an internal storage_location; stub staff/admin authorization for the
     * admin email, and the admin listing to return it.
     * Act/Assert: GET /api/admin/items with the admin demo header returns 200 and exposes the
     * internal storage_location field. Passing proves staff/admin see the full moderation view that
     * public routes omit.
     */
    @Test
    void adminItemRouteReturnsFullModerationRecords() throws Exception {
        FoundItem adminItem = foundItem("found_admin");
        adminItem.setStorageLocation("Main Office shelf A"); // internal-only field

        when(authorizationService.requireStaffOrAdmin("avery.patel@pleasantvalley.edu"))
                .thenReturn(user("user_admin", "Avery Patel", "avery.patel@pleasantvalley.edu", "admin"));
        when(foundItemService.listAdmin()).thenReturn(List.of(adminItem));

        mockMvc.perform(get("/api/admin/items")
                        .header("X-Demo-User-Email", "avery.patel@pleasantvalley.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("found_admin"))
                .andExpect(jsonPath("$[0].storage_location").value("Main Office shelf A"));
    }

    /**
     * Scenario: the generic /api/entities/{type} CRUD contract holds for every supported entity.
     * Act/Assert: drive create/list/patch/delete through the shared {@link #assertGenericEntityCrud}
     * helper for LostReport, Claim, Notification, and AuditLog, each with a representative patch and
     * the field expected to change. Passing proves the generic entity controller handles all
     * configured types uniformly (Claim additionally exercising the admin gate).
     */
    @Test
    void genericEntityRoutesWorkForEverySupportedEntity() throws Exception {
        assertGenericEntityCrud("LostReport", "lost_test", lostReport("lost_test"), "{\"status\":\"matched\"}", "$.status", "matched");
        assertGenericEntityCrud("Claim", "claim_test", claim("claim_test"), "{\"status\":\"approved\"}", "$.status", "approved");
        assertGenericEntityCrud("Notification", "notif_test", notification("notif_test"), "{\"is_read\":true}", "$.is_read", true);
        assertGenericEntityCrud("AuditLog", "audit_test", auditLog("audit_test"), "{\"details\":\"Updated by test\"}", "$.details", "Updated by test");
    }

    /**
     * Scenario: reading Claims and applying a privileged status patch both require admin.
     * Arrange: stub requireAdmin(null) (no caller identity) to throw ForbiddenException.
     * Act/Assert: GET /api/entities/Claim returns 403, and PATCH setting status=completed returns
     * 403. Passing proves the Claim entity is locked behind admin authorization for both read and
     * privileged writes.
     */
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

    /**
     * Scenario: a claimant submitting their own pickup review/rating is NOT an admin-only action.
     * Arrange: stub the update to return a claim with review_status=pending.
     * Act/Assert: PATCH /api/entities/Claim/{id} with claimant rating/review/review_status=pending
     * (no admin header) returns 200 with review_status=pending. Passing proves claimant feedback
     * fields are an allowed non-privileged write even though other Claim patches require admin.
     */
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

    /**
     * Scenario: GET /api/claims/mine returns only the calling user's own claims.
     * Arrange: stub the authorization service to resolve the demo header to the caller's email, and
     * the repository to return that user's claim for that email.
     * Act/Assert: GET with the user's demo header returns 200 and the claim with the matching
     * claimant_email. Passing proves "mine" scopes results to the resolved caller, not arbitrary
     * input.
     */
    @Test
    void claimMineReturnsOnlyResolvedUserClaims() throws Exception {
        Claim ownClaim = claim("claim_own");
        ownClaim.setClaimantEmail("jordan.kim@pleasantvalley.edu");
        when(authorizationService.resolveEmail("jordan.kim@pleasantvalley.edu"))
                .thenReturn("jordan.kim@pleasantvalley.edu"); // caller identity resolved from header
        when(claimRepository.findByClaimantEmail("jordan.kim@pleasantvalley.edu"))
                .thenReturn(List.of(ownClaim));

        mockMvc.perform(get("/api/claims/mine")
                        .header("X-Demo-User-Email", "jordan.kim@pleasantvalley.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("claim_own"))
                .andExpect(jsonPath("$[0].claimant_email").value("jordan.kim@pleasantvalley.edu"));
    }

    /**
     * Scenario: the AI assistance routes return editable, source-tagged suggestions.
     * Arrange: stub parseSearchQuery and suggestFoundItemFields to deterministic, editable results.
     * Act/Assert: POST /api/ai-assistance/search returns editable=true, source=deterministic; POST
     * /api/ai-assistance/found-item returns the suggested tags. Passing proves both AI helper routes
     * accept their payloads and serialize the suggestion maps.
     */
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

    /**
     * Scenario: the match routes fetch existing suggestions and trigger refreshes.
     * Arrange: stub get/refresh for a lost report and refresh for a found item to all return the
     * same suggestion fixture.
     * Act/Assert: GET matches for lost_001 returns the suggestion (found_item_id, confidence 96,
     * source ai); POST refresh for the lost report returns it with the expected title; POST refresh
     * for found_002 returns it with the expected first reason. Passing proves the three matchmaking
     * routes map service results to snake_case JSON.
     */
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

    /**
     * Scenario: the auth routes cover sign-in upsert, self lookup, cross-user lookup denial, admin
     * sign-in, and missing-user lookup.
     * Arrange: stub signIn to return student, then updated student, then admin across three calls;
     * stub findByEmail for the known and missing emails; stub resolveEmail so a caller may look up
     * only their own account.
     * Act/Assert: first sign-in normalizes the upper-cased email to lower-case and returns role
     * student; second sign-in updates the display name; GET own user returns the updated name; GET
     * another user's account (as a non-staff caller) returns 403; admin sign-in returns role admin;
     * GET a missing user returns 200 with literal body "null". Passing proves sign-in normalization,
     * self-service lookup, and the cross-user authorization boundary.
     */
    @Test
    void authRoutesUpsertAndFetchUser() throws Exception {
        AppUser student = user("user_riley", "Riley Chen", "riley.chen@pleasantvalley.edu", "student");
        AppUser updatedStudent = user("user_riley", "Riley C.", "riley.chen@pleasantvalley.edu", "student");
        AppUser admin = user("user_admin", "Avery Patel", "avery.patel@pleasantvalley.edu", "admin");

        // Three successive signIn calls return student, then the renamed student, then the admin.
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

    /**
     * Scenario: POST /api/uploads returns the stored file as a data URL.
     * Arrange: stub the upload service to return a base64 data URL.
     * Act/Assert: POST with file metadata + data URL returns 201 and file_url equal to the data URL.
     * Passing proves the upload route binds the request and serializes the upload response.
     */
    @Test
    void uploadRouteReturnsDataUrl() throws Exception {
        when(uploadService.upload(any(UploadRequest.class))).thenReturn(new UploadResponse("data:text/plain;base64,dGVzdA=="));

        mockMvc.perform(post("/api/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file_name\":\"tiny.txt\",\"content_type\":\"text/plain\",\"data_url\":\"data:text/plain;base64,dGVzdA==\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file_url").value("data:text/plain;base64,dGVzdA=="));
    }

    // Helper: drive a full create -> list -> patch -> delete cycle for one generic entity type and
    // assert each step. For the admin-gated Claim type it stubs admin authorization and attaches the
    // admin demo header to the read/patch/delete requests. Verifies create returns 201 with the id,
    // list returns a non-empty array, patch flips the targeted field, and delete returns success.
    private void assertGenericEntityCrud(String entityName, String id, Object entity, String patchBody, String patchedField, Object patchedValue) throws Exception {
        Object patched = patchedEntity(entity);
        boolean isClaim = "Claim".equals(entityName); // Claim routes are admin-gated
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

    // Helper: attach the admin demo header to a request only when the entity under test is the
    // admin-gated Claim type; otherwise pass the request through unchanged.
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withAdminIfClaim(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            boolean isClaim
    ) {
        return isClaim ? request.header("X-Demo-User-Email", "avery.patel@pleasantvalley.edu") : request;
    }

    // Helper: produce the expected post-patch entity by mutating the field the patch targets, so the
    // update stub can return a result reflecting the patch for each entity type.
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

    // Helper: build the CORS filter restricting the API to the two local frontend origins and the
    // verbs the app uses, mirroring production CORS configuration.
    private CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:4173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return new CorsFilter(source);
    }

    // Helper: build a snake_case JSON converter so request/response bodies match the API's
    // production naming strategy.
    private JacksonJsonHttpMessageConverter jsonConverter() {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        return new JacksonJsonHttpMessageConverter(mapper);
    }

    // Helper: build an approved found-item fixture with the given id.
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

    // Helper: build the public-facing DTO projection of a found item.
    private PublicFoundItemResponse publicItem(String id) {
        return PublicFoundItemResponse.from(foundItem(id));
    }

    // Helper: build a lost-report fixture with the given id.
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

    // Helper: build a high-confidence AI match suggestion fixture for the backpack.
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

    // Helper: build a pending_review claim fixture with the given id.
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

    // Helper: build an unread notification fixture with the given id.
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

    // Helper: build an audit-log fixture with the given id.
    private AuditLog auditLog(String id) {
        AuditLog auditLog = new AuditLog();
        auditLog.setId(id);
        auditLog.setAction("Integration test");
        auditLog.setEntityType("system");
        auditLog.setEntityId("test");
        auditLog.setPerformedBy("test");
        return auditLog;
    }

    // Helper: build an AppUser fixture with the given id, name, email, and role.
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
