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

/**
 * AI matchmaking client backed by an OpenAI-compatible chat-completions endpoint.
 *
 * <p>Implements {@link AiMatchClient}: given a lost report and a shortlist of candidate
 * found items, it asks the model to judge which candidates describe the same physical
 * object and to return per-candidate confidence scores and short reasons. The result is
 * advisory only — it augments the deterministic local scorer in {@code MatchmakingService}
 * and is never required for matching to function.
 *
 * <p>Design/safety notes:
 * <ul>
 *   <li>Fails soft: any disablement, missing key, or transport/parse error returns an
 *       empty list so callers fall back to local scores.</li>
 *   <li>Privacy: only item-descriptive fields are sent; contact data is never included.</li>
 *   <li>Robust parsing: tolerates fenced/markdown-wrapped JSON and both snake_case and
 *       camelCase field names, and clamps confidences to 0-100.</li>
 * </ul>
 *
 * <p>Collaborators: Jackson {@link ObjectMapper} (JSON serialize/parse), Spring
 * {@link RestClient} (HTTP), and the configured OpenAI-compatible API.
 */
@Service
public class OpenAiMatchClient implements AiMatchClient {
    /** Logger; used to record (at WARN) when the AI path is unavailable so matching degrades silently. */
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiMatchClient.class);

    /** Jackson mapper used to serialize the request payload and parse the model's JSON reply. */
    private final ObjectMapper objectMapper;
    /** HTTP client used to call the chat-completions endpoint. */
    private final RestClient restClient;
    /** Feature flag ({@code app.ai.matchmaking.enabled}); when false the client is a no-op. */
    private final boolean enabled;
    /** API bearer key ({@code app.ai.matchmaking.api-key}); blank disables the AI path. */
    private final String apiKey;
    /** Chat-completions endpoint URL ({@code app.ai.matchmaking.base-url}). */
    private final String baseUrl;
    /** Model identifier to request ({@code app.ai.matchmaking.model}). */
    private final String model;

    /**
     * @param objectMapper Jackson mapper for JSON
     * @param enabled      whether AI matchmaking is enabled
     * @param apiKey       API key (trimmed; treated as empty when null)
     * @param baseUrl      chat-completions endpoint URL
     * @param model        model name to invoke
     */
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

    /**
     * Ranks candidate found items against a lost report using the AI model.
     *
     * <p>Short-circuits to an empty list when AI is disabled, no API key is set, or there
     * are no candidates. Otherwise it POSTs a chat-completion request (system prompt +
     * JSON payload of the report and candidates), then parses the model's structured reply.
     *
     * @param report     the lost report being matched
     * @param candidates pre-shortlisted found items to evaluate
     * @return per-candidate {@link AiMatchResult}s; an empty list on disablement or any
     *         transport/parse failure (caller then relies on local scores). Never throws.
     */
    @Override
    public List<AiMatchResult> rankMatches(LostReport report, List<FoundItem> candidates) {
        // Guard: skip the network call entirely when AI is off or there is nothing to rank.
        if (!enabled || apiKey.isBlank() || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        try {
            // Issue the chat-completions POST with bearer auth and the constructed body.
            String response = restClient.post()
                    .uri(URI.create(baseUrl))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(requestBody(report, candidates))
                    .retrieve()
                    .body(String.class);
            // Translate the raw HTTP body into typed match results.
            return parseResults(response);
        } catch (RestClientException | IllegalArgumentException | JsonProcessingException exception) {
            // Fail soft: log and let the deterministic scorer stand in.
            LOGGER.warn("AI matchmaking unavailable, using local match scores: {}", exception.getMessage());
            return List.of();
        }
    }

    /**
     * Builds the chat-completions request body.
     *
     * <p>Composes two messages: a {@code system} prompt that pins the task (decide if two
     * lost/found records are the same object), fixes the exact JSON response shape, bounds
     * confidence to 0-100, and forbids using contact data; and a {@code user} message whose
     * content is a JSON document containing the sanitized lost-report payload and the list
     * of candidate found-item payloads. Temperature is held low (0.1) for deterministic output.
     *
     * @param report     the lost report
     * @param candidates candidate found items
     * @return a map serializable to the chat-completions request JSON
     * @throws JsonProcessingException if the embedded user payload cannot be serialized
     */
    private Map<String, Object> requestBody(LostReport report, List<FoundItem> candidates) throws JsonProcessingException {
        return Map.of(
                "model", model,
                // Low temperature -> stable, repeatable judgments.
                "temperature", 0.1,
                "messages", List.of(
                        // System message: defines the task and the strict JSON contract.
                        Map.of(
                                "role", "system",
                                "content", """
                                        You evaluate whether lost-and-found records describe the same physical item.
                                        Return JSON only in this exact shape:
                                        {"matches":[{"found_item_id":"found_123","confidence":0,"reasons":["short reason"]}]}
                                        Confidence must be 0-100. Use only item details, never contact data.
                                        """
                        ),
                        // User message: the data to evaluate, serialized as a JSON string.
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

    /**
     * Builds the privacy-safe payload describing the lost report.
     *
     * <p>Includes only descriptive fields (title, category, description, color, brand,
     * location/date/time lost). Blank/empty fields are omitted to keep the prompt lean.
     * Deliberately excludes any contact/owner data.
     *
     * @param report the lost report
     * @return an ordered map of present descriptive fields
     */
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

    /**
     * Builds the privacy-safe payload describing a single candidate found item.
     *
     * <p>Includes only descriptive fields (title, category/subcategory, descriptions,
     * distinguishing features, color, brand, location/date/time found, tags), with blank
     * or empty values omitted. The {@code id} is included so the model can reference each
     * candidate by {@code found_item_id} in its response. No contact data is sent.
     *
     * @param item the candidate found item
     * @return an ordered map of present descriptive fields
     */
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

    /**
     * Parses the chat-completion HTTP response into typed match results.
     *
     * <p>Steps:
     * <ol>
     *   <li>read the top-level JSON and extract the assistant message content at
     *       {@code choices[0].message.content};</li>
     *   <li>parse that content as JSON (after stripping any markdown fences via
     *       {@link #jsonOnly(String)}); if no content field exists, fall back to treating
     *       the whole response as the result JSON;</li>
     *   <li>require a {@code matches} array, else return empty;</li>
     *   <li>for each match: read the found-item id (snake_case or camelCase), skip
     *       entries lacking an id, collect non-blank reasons, and clamp confidence to 0-100.</li>
     * </ol>
     *
     * @param response the raw HTTP response body (may be null/blank)
     * @return parsed match results; empty when the body is blank or has no valid matches
     * @throws JsonProcessingException if the JSON cannot be read
     */
    private List<AiMatchResult> parseResults(String response) throws JsonProcessingException {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        // Top-level chat-completions envelope.
        JsonNode root = objectMapper.readTree(response);
        // The model's answer lives in the first choice's message content.
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        // Parse the inner JSON answer (de-fenced); fall back to the envelope itself if empty.
        JsonNode resultRoot = content.isBlank() ? root : objectMapper.readTree(jsonOnly(content));
        JsonNode matches = resultRoot.path("matches");
        // Without a matches array there is nothing to return.
        if (!matches.isArray()) {
            return List.of();
        }

        List<AiMatchResult> results = new ArrayList<>();
        for (JsonNode match : matches) {
            // Accept either snake_case or camelCase id naming from the model.
            String foundItemId = match.path("found_item_id").asText("");
            if (foundItemId.isBlank()) {
                foundItemId = match.path("foundItemId").asText("");
            }
            // An entry without an id is unusable; skip it.
            if (foundItemId.isBlank()) {
                continue;
            }

            // Collect any non-blank human-readable reasons.
            List<String> reasons = new ArrayList<>();
            JsonNode reasonNodes = match.path("reasons");
            if (reasonNodes.isArray()) {
                reasonNodes.forEach(reason -> {
                    if (!reason.asText("").isBlank()) {
                        reasons.add(reason.asText());
                    }
                });
            }

            // Clamp confidence into the valid 0-100 band before recording the result.
            results.add(new AiMatchResult(foundItemId, clamp(match.path("confidence").asInt(0)), reasons));
        }
        return results;
    }

    /**
     * Adds {@code key -> value} to {@code payload} only when the value carries content.
     *
     * <p>Skips nulls, blank strings, and empty lists so the prompt payload omits noise.
     *
     * @param payload target map to mutate
     * @param key     field name
     * @param value   candidate value
     */
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

    /**
     * Extracts the bare JSON object from model output that may be wrapped.
     *
     * <p>Handling: trims whitespace; strips a leading/trailing markdown code fence
     * (``` or ```json); then narrows to the substring between the first {@code &#123;} and
     * the last {@code &#125;} so any surrounding prose is discarded. Returns the input
     * unchanged when no braces are found.
     *
     * @param content raw assistant message content
     * @return the best-effort JSON-object substring
     */
    private String jsonOnly(String content) {
        String normalized = content.trim();
        // Remove a surrounding ```/```json code fence if present.
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        // Keep only the outermost { ... } object, dropping any leading/trailing prose.
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return normalized.substring(start, end + 1);
        }
        return normalized;
    }

    /**
     * Clamps a confidence value into the inclusive 0-100 range.
     *
     * @param value raw confidence from the model
     * @return {@code value} bounded to [0, 100]
     */
    private Integer clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
