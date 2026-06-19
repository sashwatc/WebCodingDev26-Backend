package com.FBLA.WebCodingDev26Backend.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:api-test;DB_CLOSE_DELAY=-1",
        "app.seed.enabled=true"
})
class ApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthRouteReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
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
    void seedDataIsAvailableAfterStartup() throws Exception {
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
        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));

        String body = """
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
                """;

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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
                                    "rating": 5,
                                    "review": "Smooth pickup",
                                    "claimant_name": "Jordan Kim",
                                    "reviewer_email": "jordan.kim@pleasantvalley.edu",
                                    "review_status": "approved"
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
        assertGenericEntityCrud(
                "LostReport",
                "lost_test",
                """
                {
                  "id": "lost_test",
                  "title": "Lost Hoodie",
                  "category": "clothing",
                  "description": "Black hoodie",
                  "contact_name": "Jordan Kim",
                  "contact_email": "jordan.kim@pleasantvalley.edu",
                  "matched_items": ["found_001"]
                }
                """,
                "$.contact_email",
                "jordan.kim@pleasantvalley.edu",
                "{\"status\":\"matched\"}",
                "$.status",
                "matched"
        );

        assertGenericEntityCrud(
                "Claim",
                "claim_test",
                """
                {
                  "id": "claim_test",
                  "found_item_id": "found_001",
                  "claimant_name": "Riley Chen",
                  "claimant_email": "riley.chen@pleasantvalley.edu",
                  "claim_reason": "I can identify the item.",
                  "risk_flags": ["sufficient detail provided"]
                }
                """,
                "$.found_item_id",
                "found_001",
                "{\"status\":\"approved\"}",
                "$.status",
                "approved"
        );

        assertGenericEntityCrud(
                "Notification",
                "notif_test",
                """
                {
                  "id": "notif_test",
                  "user_email": "riley.chen@pleasantvalley.edu",
                  "title": "Claim update",
                  "message": "Your claim was reviewed.",
                  "type": "claim_update",
                  "is_read": false
                }
                """,
                "$.user_email",
                "riley.chen@pleasantvalley.edu",
                "{\"is_read\":true}",
                "$.is_read",
                true
        );

        assertGenericEntityCrud(
                "AuditLog",
                "audit_test",
                """
                {
                  "id": "audit_test",
                  "action": "Integration test",
                  "entity_type": "system",
                  "entity_id": "test",
                  "performed_by": "test"
                }
                """,
                "$.entity_type",
                "system",
                "{\"details\":\"Updated by test\"}",
                "$.details",
                "Updated by test"
        );
    }

    @Test
    void authRoutesUpsertAndFetchUser() throws Exception {
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
        mockMvc.perform(post("/api/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file_name\":\"tiny.txt\",\"content_type\":\"text/plain\",\"data_url\":\"data:text/plain;base64,dGVzdA==\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file_url").value("data:text/plain;base64,dGVzdA=="));
    }

    private void assertGenericEntityCrud(
            String entityName,
            String id,
            String body,
            String createdField,
            Object createdValue,
            String patchBody,
            String patchedField,
            Object patchedValue
    ) throws Exception {
        mockMvc.perform(post("/api/entities/" + entityName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath(createdField).value(createdValue));

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
}
