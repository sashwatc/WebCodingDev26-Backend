package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class OpenAiMatchClient implements AiMatchClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiMatchClient.class);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final boolean enabled;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public OpenAiMatchClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.matchmaking.enabled:true}") boolean enabled,
            @Value("${app.ai.matchmaking.api-key:}") String apiKey,
            @Value("${app.ai.matchmaking.base-url:https://api.openai.com/v1/chat/completions}") String baseUrl,
            @Value("${app.ai.matchmaking.model:gpt-4o-mini}") String model
    ) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public List<AiMatchResult> rankMatches(LostReport report, List<FoundItem> candidates) {
        if (!enabled || apiKey.isBlank() || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        try {
            String response = restClient.post()
                    .uri(URI.create(baseUrl))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(requestBody(report, candidates))
                    .retrieve()
                    .body(String.class);
            return parseResults(response);
        } catch (RestClientException | IllegalArgumentException | JsonProcessingException exception) {
            LOGGER.warn("AI matchmaking unavailable, using local match scores: {}", exception.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> requestBody(LostReport report, List<FoundItem> candidates) throws JsonProcessingException {
        return Map.of(
                "model", model,
                "temperature", 0.1,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", """
                                        You evaluate whether lost-and-found records describe the same physical item.
                                        Return JSON only in this exact shape:
                                        {"matches":[{"found_item_id":"found_123","confidence":0,"reasons":["short reason"]}]}
                                        Confidence must be 0-100. Use only item details, never contact data.
                                        """
                        ),
                        Map.of(
                                "role", "user",
                                "content", objectMapper.writeValueAsString(Map.of(
                                        "lost_report", lostReportPayload(report),
                                        "candidate_found_items", candidates.stream().map(this::foundItemPayload).toList()
                                ))
                        )
                )
        );
    }

    private Map<String, Object> lostReportPayload(LostReport report) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "id", report.getId());
        putIfPresent(payload, "title", report.getTitle());
        putIfPresent(payload, "category", report.getCategory());
        putIfPresent(payload, "description", report.getDescription());
        putIfPresent(payload, "color", report.getColor());
        putIfPresent(payload, "brand", report.getBrand());
        putIfPresent(payload, "location_lost", report.getLocationLost());
        putIfPresent(payload, "date_lost", report.getDateLost());
        putIfPresent(payload, "time_lost", report.getTimeLost());
        return payload;
    }

    private Map<String, Object> foundItemPayload(FoundItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "id", item.getId());
        putIfPresent(payload, "title", item.getTitle());
        putIfPresent(payload, "category", item.getCategory());
        putIfPresent(payload, "subcategory", item.getSubcategory());
        putIfPresent(payload, "description", item.getDescription());
        putIfPresent(payload, "ai_description", item.getAiDescription());
        putIfPresent(payload, "distinguishing_features", item.getDistinguishingFeatures());
        putIfPresent(payload, "color", item.getColor());
        putIfPresent(payload, "brand", item.getBrand());
        putIfPresent(payload, "location_found", item.getLocationFound());
        putIfPresent(payload, "date_found", item.getDateFound());
        putIfPresent(payload, "time_found", item.getTimeFound());
        putIfPresent(payload, "tags", item.getTags());
        return payload;
    }

    private List<AiMatchResult> parseResults(String response) throws JsonProcessingException {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        JsonNode root = objectMapper.readTree(response);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        JsonNode resultRoot = content.isBlank() ? root : objectMapper.readTree(jsonOnly(content));
        JsonNode matches = resultRoot.path("matches");
        if (!matches.isArray()) {
            return List.of();
        }

        List<AiMatchResult> results = new ArrayList<>();
        for (JsonNode match : matches) {
            String foundItemId = match.path("found_item_id").asText("");
            if (foundItemId.isBlank()) {
                foundItemId = match.path("foundItemId").asText("");
            }
            if (foundItemId.isBlank()) {
                continue;
            }

            List<String> reasons = new ArrayList<>();
            JsonNode reasonNodes = match.path("reasons");
            if (reasonNodes.isArray()) {
                reasonNodes.forEach(reason -> {
                    if (!reason.asText("").isBlank()) {
                        reasons.add(reason.asText());
                    }
                });
            }

            results.add(new AiMatchResult(foundItemId, clamp(match.path("confidence").asInt(0)), reasons));
        }
        return results;
    }

    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        payload.put(key, value);
    }

    private String jsonOnly(String content) {
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return normalized.substring(start, end + 1);
        }
        return normalized;
    }

    private Integer clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
