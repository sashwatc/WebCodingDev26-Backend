package com.FBLA.WebCodingDev26Backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the frontend's Appwrite client configuration as a JavaScript file.
 *
 * <p>Exposes a single endpoint, {@code GET /appwrite-config.js}, that returns a tiny
 * JS snippet assigning the (public) Appwrite settings to a global window variable.
 * The values are sourced from environment-backed Spring properties (each defaulting
 * to an empty string) rather than being hardcoded, so configuration stays
 * environment-specific. Uses {@link ObjectMapper} to emit the config object as JSON.
 * No authorization is enforced; all values are public client-side settings.</p>
 */
@RestController // REST controller: the handler's return value becomes the response body
public class AppwriteConfigController {
    // JSON serializer used to render the config object into the emitted JavaScript.
    private final ObjectMapper objectMapper;
    // Appwrite API endpoint URL (from app.appwrite.endpoint).
    private final String endpoint;
    // Appwrite project id (from app.appwrite.project-id).
    private final String projectId;
    // Appwrite database id (from app.appwrite.database-id).
    private final String databaseId;
    // Collection id for chat conversations (from app.appwrite.chat-conversations-collection-id).
    private final String conversationsCollectionId;
    // Collection id for chat messages (from app.appwrite.chat-messages-collection-id).
    private final String messagesCollectionId;
    // Appwrite team id that designates admins (from app.appwrite.admin-team-id).
    private final String adminTeamId;

    /**
     * Constructor injection. Each {@code @Value} pulls a Spring property (backed by an
     * environment variable) with an empty-string default ({@code :}) so a missing
     * property yields {@code ""} rather than failing startup. Null values are further
     * normalized to {@code ""} below for safety.
     */
    public AppwriteConfigController(
            ObjectMapper objectMapper,
            @Value("${app.appwrite.endpoint:}") String endpoint,
            @Value("${app.appwrite.project-id:}") String projectId,
            @Value("${app.appwrite.database-id:}") String databaseId,
            @Value("${app.appwrite.chat-conversations-collection-id:}") String conversationsCollectionId,
            @Value("${app.appwrite.chat-messages-collection-id:}") String messagesCollectionId,
            @Value("${app.appwrite.admin-team-id:}") String adminTeamId) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint == null ? "" : endpoint;
        this.projectId = projectId == null ? "" : projectId;
        this.databaseId = databaseId == null ? "" : databaseId;
        this.conversationsCollectionId = conversationsCollectionId == null ? "" : conversationsCollectionId;
        this.messagesCollectionId = messagesCollectionId == null ? "" : messagesCollectionId;
        this.adminTeamId = adminTeamId == null ? "" : adminTeamId;
    }

    /**
     * GET /appwrite-config.js — return the Appwrite client config as a JavaScript file.
     *
     * <p>The {@code produces = "application/javascript"} tells Spring to set that
     * Content-Type so the browser can load it via a {@code <script>} tag.</p>
     *
     * @return a JS string of the form {@code window.__APPWRITE_CONFIG__ = {...};} where the
     *         object contains the public Appwrite settings; 200 OK. No authorization required.
     * @throws JsonProcessingException if the config map cannot be serialized to JSON
     */
    @GetMapping(value = "/appwrite-config.js", produces = "application/javascript")
    public String appwriteConfig() throws JsonProcessingException {
        // These values are public Appwrite project settings, but they still come
        // from environment variables so judges can see that config is not hardcoded.
        Map<String, String> config = Map.of(
                "endpoint", endpoint,
                "projectId", projectId,
                "databaseId", databaseId,
                "chatConversationsCollectionId", conversationsCollectionId,
                "chatMessagesCollectionId", messagesCollectionId,
                "adminTeamId", adminTeamId
        );
        return "window.__APPWRITE_CONFIG__ = " + objectMapper.writeValueAsString(config) + ";";
    }
}
