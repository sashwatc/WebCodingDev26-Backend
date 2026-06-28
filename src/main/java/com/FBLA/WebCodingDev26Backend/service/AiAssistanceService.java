package com.FBLA.WebCodingDev26Backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Provides optional AI-style assistance for two intake/search tasks:
 * <ol>
 *   <li>{@link #suggestFoundItemFields} — suggests editable found-item intake fields
 *       (category, color, brand, tags) from an item's public title/description/photo.</li>
 *   <li>{@link #parseSearchQuery} — parses a free-text lost-and-found search query into
 *       structured, editable filters (keywords, category, color, brand, location, date hint).</li>
 * </ol>
 *
 * <p>Design: every result is produced by a fully <strong>deterministic</strong> rule-based
 * engine (keyword dictionaries for categories/locations/brands/colors). When an Ollama
 * vision/text model is configured and enabled, its JSON output is merged on top as a
 * best-effort enhancement; any Ollama failure silently falls back to the deterministic
 * result. The service never decides ownership or approves claims — it only proposes
 * editable suggestions. Collaborators: {@link ObjectMapper} (JSON), a Spring
 * {@link RestClient} (HTTP to a local Ollama server).
 */
@Service
public class AiAssistanceService {
    /** Logger; used to warn (without failing) when optional Ollama assistance is unavailable. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AiAssistanceService.class);
    /** Matches the leading {@code data:image/...;base64,} prefix of a data-URL so it can be stripped to raw base64. */
    private static final Pattern DATA_URL_PATTERN = Pattern.compile("^data:image/[^;]+;base64,", Pattern.CASE_INSENSITIVE);
    /** Common words excluded from generated tag/keyword lists so only meaningful tokens remain. */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "after", "an", "and", "at", "by", "for", "from", "i", "in", "is", "it", "lost", "near",
            "of", "on", "the", "to", "was", "with"
    );
    /** Recognized color vocabulary; the first color found in the text is used. */
    private static final List<String> COLORS = List.of(
            "Black", "White", "Red", "Blue", "Green", "Yellow", "Orange", "Purple", "Pink", "Brown", "Gray", "Silver", "Gold"
    );
    /** Category -> keyword list. First category with any matching keyword in the text wins. Insertion-ordered so earlier categories take priority. */
    private static final Map<String, List<String>> CATEGORY_KEYWORDS;
    static {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("electronics", List.of("airpods", "earbuds", "headphones", "phone", "charger", "laptop", "calculator", "watch", "tablet"));
        map.put("clothing", List.of("hoodie", "jacket", "shirt", "sweater", "coat", "hat", "gloves"));
        map.put("accessories", List.of("wallet", "purse", "glasses", "sunglasses", "bracelet"));
        map.put("school_supplies", List.of("notebook", "binder", "book", "pencil", "pen", "folder"));
        map.put("keys_ids", List.of("keys", "key", "id", "badge", "lanyard"));
        map.put("food_containers", List.of("bottle", "hydro", "flask", "lunchbox", "thermos", "container"));
        map.put("sports_equipment", List.of("ball", "cleats", "kneepads", "pads", "racket", "helmet", "gym bag"));
        map.put("bags_cases", List.of("backpack", "bag", "case", "pouch", "tote"));
        map.put("personal_items", List.of("ring", "necklace", "makeup", "medicine", "planner"));
        CATEGORY_KEYWORDS = Collections.unmodifiableMap(map);
    }
    /** Canonical location name -> keyword list. First location with a matching keyword wins; insertion-ordered for priority. */
    private static final Map<String, List<String>> LOCATIONS;
    static {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("Gymnasium", List.of("gym", "gymnasium", "bleachers", "game", "athletic"));
        map.put("Cafeteria", List.of("cafeteria", "lunch"));
        map.put("Library", List.of("library"));
        map.put("Main Office", List.of("office", "front desk"));
        map.put("Science Hall", List.of("science", "lab"));
        map.put("Auditorium", List.of("auditorium"));
        map.put("Bus Loop", List.of("bus"));
        map.put("Football Field", List.of("football", "field", "stadium"));
        map.put("Student Lounge", List.of("student lounge", "lounge"));
        map.put("Computer Lab", List.of("computer lab", "computer"));
        LOCATIONS = Collections.unmodifiableMap(map);
    }
    /** Recognized brand vocabulary; first brand found in the text is used. */
    private static final List<String> BRANDS = List.of(
            "Apple", "AirPods", "Beats", "Samsung", "Sony", "Nike", "Adidas", "Hydro Flask", "JanSport",
            "Texas Instruments", "TI", "Dell", "HP", "Lenovo"
    );

    /** Jackson mapper for serializing prompts and parsing Ollama JSON responses. */
    private final ObjectMapper objectMapper;
    /** HTTP client used to call the local Ollama generate endpoint. */
    private final RestClient restClient;
    /** Feature flag: when false (default) Ollama is never called and only deterministic results are returned. */
    private final boolean ollamaEnabled;
    /** Base URL of the Ollama server (trailing slashes trimmed); blank disables Ollama calls. */
    private final String ollamaUrl;
    /** Name of the Ollama model to invoke (default {@code llava}, a vision model). */
    private final String ollamaModel;

    /**
     * Injects the JSON mapper and Ollama configuration. Creates the RestClient and
     * normalizes the Ollama URL by trimming trailing slashes. No network calls here.
     */
    public AiAssistanceService(
            ObjectMapper objectMapper,
            @Value("${app.ai.assistance.ollama-enabled:false}") boolean ollamaEnabled,
            @Value("${app.ai.assistance.ollama-url:http://localhost:11434}") String ollamaUrl,
            @Value("${app.ai.assistance.ollama-model:llava}") String ollamaModel
    ) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
        this.ollamaEnabled = ollamaEnabled;
        this.ollamaUrl = trimTrailingSlash(ollamaUrl);
        this.ollamaModel = ollamaModel;
    }

    /**
     * Suggests editable found-item intake fields (category, color, brand, tags) from
     * public item details only.
     *
     * <p>Steps: (1) reduce the input to a whitelist of safe public fields; (2) compute
     * deterministic suggestions; (3) attempt Ollama enhancement; (4) merge Ollama over
     * the deterministic base. The result is always editable and never asserts ownership.
     *
     * @param input raw request map (title/description/file_name/photo_urls)
     * @return suggestion map including {@code source}/{@code used_ollama}/{@code editable} flags
     */
    public Map<String, Object> suggestFoundItemFields(Map<String, Object> input) {
        Map<String, Object> safe = safeFoundItemInput(input);
        Map<String, Object> deterministic = deterministicFoundItemSuggestions(safe);
        Map<String, Object> ollama = askOllamaForFoundItem(safe);
        return mergeSuggestion(deterministic, ollama, "field suggestions");
    }

    /**
     * Parses a free-text search query into structured, editable filters.
     *
     * @param input raw request map; the {@code query} key holds the search text
     * @return filter map (keywords/category/color/brand/location/date_hint) plus
     *         {@code source}/{@code used_ollama}/{@code editable} flags
     */
    public Map<String, Object> parseSearchQuery(Map<String, Object> input) {
        String query = string(input == null ? null : input.get("query"));
        Map<String, Object> deterministic = deterministicSearch(query);
        Map<String, Object> ollama = askOllamaForSearch(query);
        return mergeSuggestion(deterministic, ollama, "search interpretation");
    }

    /**
     * Rule-based found-item suggestions. Concatenates title, description and filename,
     * then detects a category/color/brand and derives a tag list. Always sets the
     * deterministic-source flags and an explanation. No external calls.
     */
    private Map<String, Object> deterministicFoundItemSuggestions(Map<String, Object> safe) {
        // Combine all public text fields into one searchable blob.
        String text = String.join(" ",
                string(safe.get("title")),
                string(safe.get("description")),
                string(safe.get("file_name"))
        );
        Map<String, Object> result = new LinkedHashMap<>();
        // Only add detected fields when non-blank (putIfPresent skips empties).
        putIfPresent(result, "category", detectCategory(text));
        putIfPresent(result, "color", detectColor(text));
        putIfPresent(result, "brand", detectBrand(text));
        // Tags are seeded from the detected category/color/brand plus salient tokens.
        result.put("tags", tags(text, string(result.get("category")), string(result.get("color")), string(result.get("brand"))));
        result.put("explanation", "Deterministic fallback used public item text, filename, and visible photo metadata only.");
        result.put("source", "deterministic");
        result.put("used_ollama", false);
        result.put("editable", true);
        return result;
    }

    /**
     * Rule-based parse of a search query into filters: detects category/color/brand/
     * location, derives keyword tokens, and infers a coarse date hint. No external calls.
     */
    private Map<String, Object> deterministicSearch(String query) {
        Map<String, Object> result = new LinkedHashMap<>();
        putIfPresent(result, "category", detectCategory(query));
        putIfPresent(result, "color", detectColor(query));
        putIfPresent(result, "brand", detectBrand(query));
        putIfPresent(result, "location", detectLocation(query));
        // Keyword list (no category/color/brand seeds for searches).
        result.put("keywords", tags(query, "", "", ""));
        result.put("date_hint", detectDateHint(query));
        result.put("explanation", "Search interpretation is deterministic: keywords, color, category, location, and date hints were parsed from the query text.");
        result.put("source", "deterministic");
        result.put("used_ollama", false);
        result.put("editable", true);
        return result;
    }

    /**
     * Builds the found-item prompt (constraining the model to JSON-only output and a
     * fixed category list), attaches up to one base64 image extracted from the photos,
     * and calls Ollama. Returns an empty map when Ollama is disabled or fails.
     */
    private Map<String, Object> askOllamaForFoundItem(Map<String, Object> safe) {
        List<String> images = extractBase64Images(safe.get("photo_urls"));
        String prompt = """
                Suggest editable lost-and-found intake fields from public item details only.
                Never decide ownership or claim approval. Return JSON only:
                {"category":"","color":"","brand":"","tags":[],"explanation":""}
                Allowed categories: electronics, clothing, accessories, school_supplies, sports_equipment, food_containers, keys_ids, bags_cases, personal_items, other.
                Public item details:
                """ + toJson(safe);
        return callOllama(prompt, images);
    }

    /**
     * Builds the search-query prompt (JSON-only filters, never approving claims) and
     * calls Ollama with no images. Returns an empty map when Ollama is disabled or fails.
     */
    private Map<String, Object> askOllamaForSearch(String query) {
        String prompt = """
                Parse this lost-and-found search query into editable filters. Never approve ownership or claims.
                Return JSON only: {"keywords":[],"category":"","color":"","brand":"","location":"","date_hint":"","explanation":""}
                Query: "%s"
                """.formatted(query == null ? "" : query);
        return callOllama(prompt, List.of());
    }

    /**
     * Low-level call to the Ollama {@code /api/generate} endpoint.
     *
     * <p>Returns an empty map (never throws) when Ollama is disabled, the URL is blank,
     * or any RestClient/JSON error occurs — the caller then keeps its deterministic
     * result. On success: reads the model's {@code response} field, extracts the JSON
     * object substring, deserializes it to a map, and tags it as Ollama-assisted.
     *
     * @param prompt the fully-built instruction text
     * @param images optional list of raw base64 images for vision models
     * @return parsed model output map, or empty on disable/blank/failure
     */
    private Map<String, Object> callOllama(String prompt, List<String> images) {
        // Hard off-switch: skip all network work when disabled or unconfigured.
        if (!ollamaEnabled || ollamaUrl.isBlank()) {
            return Map.of();
        }
        // Build the non-streaming, JSON-formatted generate request body.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ollamaModel);
        body.put("prompt", prompt);
        body.put("stream", false);
        body.put("format", "json");
        if (images != null && !images.isEmpty()) {
            body.put("images", images);
        }
        try {
            // POST to Ollama and read the raw JSON envelope as a string.
            String raw = restClient.post()
                    .uri(URI.create(ollamaUrl + "/api/generate"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            // The model's actual text output lives under "response".
            String response = root.path("response").asText("");
            if (response.isBlank()) {
                return Map.of();
            }
            // The model was asked for JSON; isolate the {...} object and parse it to a map.
            JsonNode parsed = objectMapper.readTree(jsonOnly(response));
            Map<String, Object> map = objectMapper.convertValue(parsed, new TypeReference<>() {});
            map.put("source", "ollama_assisted");
            map.put("used_ollama", true);
            return map;
        } catch (RestClientException | IllegalArgumentException | JsonProcessingException exception) {
            // Any failure is non-fatal: log and let the deterministic result stand.
            LOGGER.warn("Optional Ollama assistance unavailable; using deterministic fallback: {}", exception.getMessage());
            return Map.of();
        }
    }

    /**
     * Merges Ollama output over the deterministic base, keeping all fields editable.
     *
     * <p>Rules: if Ollama produced nothing, return the deterministic map unchanged.
     * Otherwise copy the deterministic map and overlay non-blank scalar fields
     * (category/color/brand/location/date_hint/explanation) from Ollama, replace
     * keywords/tags only when Ollama supplied non-empty lists (sanitized), and stamp
     * the result as Ollama-assisted with a default explanation if none was provided.
     *
     * @param label human label of the task, used in the default explanation
     */
    private Map<String, Object> mergeSuggestion(Map<String, Object> deterministic, Map<String, Object> ollama, String label) {
        if (ollama == null || ollama.isEmpty()) {
            return deterministic;
        }
        Map<String, Object> merged = new LinkedHashMap<>(deterministic);
        // Overlay scalar fields where the model gave a non-blank value.
        for (String key : List.of("category", "color", "brand", "location", "date_hint", "explanation")) {
            putIfPresent(merged, key, ollama.get(key));
        }
        // Lists are replaced wholesale (sanitized) only when the model returned non-empty ones.
        Object keywords = ollama.get("keywords");
        Object tags = ollama.get("tags");
        if (keywords instanceof List<?> list && !list.isEmpty()) {
            merged.put("keywords", sanitizeList(list));
        }
        if (tags instanceof List<?> list && !list.isEmpty()) {
            merged.put("tags", sanitizeList(list));
        }
        merged.put("source", "ollama_assisted");
        merged.put("used_ollama", true);
        merged.put("editable", true);
        merged.putIfAbsent("explanation", "Ollama assisted the " + label + "; deterministic fields remain editable.");
        return merged;
    }

    /**
     * Reduces raw user input to a whitelist of safe, public fields before any AI use:
     * title, description, file_name, and at most one photo URL. Anything else is dropped.
     * Returns an empty map for null input.
     */
    private Map<String, Object> safeFoundItemInput(Map<String, Object> input) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (input == null) {
            return safe;
        }
        putIfPresent(safe, "title", input.get("title"));
        putIfPresent(safe, "description", input.get("description"));
        putIfPresent(safe, "file_name", input.get("file_name"));
        Object photos = input.get("photo_urls");
        // Keep at most one non-blank photo URL to limit payload/inference cost.
        if (photos instanceof List<?> list) {
            safe.put("photo_urls", list.stream().map(this::string).filter(value -> !value.isBlank()).limit(1).toList());
        }
        return safe;
    }

    /**
     * Returns the first category whose keyword list matches the lowercased text, or "".
     * Iteration order is the insertion order of {@link #CATEGORY_KEYWORDS}.
     */
    private String detectCategory(String text) {
        String lowered = lower(text);
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(lowered::contains)) {
                return entry.getKey();
            }
        }
        return "";
    }

    /** Returns the first {@link #COLORS} entry contained in the lowercased text, or "". */
    private String detectColor(String text) {
        String lowered = lower(text);
        return COLORS.stream().filter(color -> lowered.contains(color.toLowerCase(Locale.ROOT))).findFirst().orElse("");
    }

    /** Returns the first {@link #BRANDS} entry contained in the lowercased text, or "". */
    private String detectBrand(String text) {
        String lowered = lower(text);
        return BRANDS.stream().filter(brand -> lowered.contains(brand.toLowerCase(Locale.ROOT))).findFirst().orElse("");
    }

    /**
     * Returns the first location whose keyword list matches the lowercased text, or "".
     * Iteration order is the insertion order of {@link #LOCATIONS}.
     */
    private String detectLocation(String text) {
        String lowered = lower(text);
        for (Map.Entry<String, List<String>> entry : LOCATIONS.entrySet()) {
            if (entry.getValue().stream().anyMatch(lowered::contains)) {
                return entry.getKey();
            }
        }
        return "";
    }

    /**
     * Infers a coarse date hint from recognized phrases in the query
     * (today / yesterday / after game / this week), or "" if none match.
     */
    private String detectDateHint(String query) {
        String lowered = lower(query);
        if (lowered.contains("today")) return "today";
        if (lowered.contains("yesterday")) return "yesterday";
        if (lowered.contains("after game") || lowered.contains("after the game")) return "after game";
        if (lowered.contains("this week")) return "this week";
        return "";
    }

    /**
     * Derives an ordered, de-duplicated tag/keyword list (max 8) from the text.
     *
     * <p>Algorithm: first seed the set with any non-blank {@code extras} (underscores
     * to spaces, lowercased) so detected category/color/brand appear first; then
     * tokenize the text (strip non-alphanumerics), adding tokens longer than two
     * characters that are not stop words, stopping once 8 entries are collected.
     * A {@link LinkedHashSet} preserves order and removes duplicates.
     */
    private List<String> tags(String text, String... extras) {
        Set<String> values = new LinkedHashSet<>();
        for (String extra : extras) {
            if (extra != null && !extra.isBlank()) {
                values.add(extra.replace('_', ' ').toLowerCase(Locale.ROOT));
            }
        }
        for (String token : lower(text).replaceAll("[^a-z0-9\\s]", " ").split("\\s+")) {
            if (token.length() > 2 && !STOP_WORDS.contains(token)) {
                values.add(token);
            }
            if (values.size() >= 8) {
                break;
            }
        }
        return new ArrayList<>(values);
    }

    /**
     * Extracts up to one raw base64 image string from the photo list: keeps only
     * values matching the {@code data:image/...;base64,} prefix and strips that prefix.
     * Returns an empty list when the input is not a list or has no data-URL images.
     */
    private List<String> extractBase64Images(Object rawPhotos) {
        if (!(rawPhotos instanceof List<?> photos)) {
            return List.of();
        }
        return photos.stream()
                .map(this::string)
                .filter(value -> DATA_URL_PATTERN.matcher(value).find())
                .map(value -> DATA_URL_PATTERN.matcher(value).replaceFirst(""))
                .limit(1)
                .toList();
    }

    /** Normalizes a model-supplied list to non-blank trimmed strings, capped at 8 items. */
    private List<String> sanitizeList(List<?> raw) {
        return raw.stream().map(this::string).filter(value -> !value.isBlank()).limit(8).toList();
    }

    /** Puts the stringified value under {@code key} only when it is non-blank. */
    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        String text = string(value);
        if (!text.isBlank()) {
            payload.put(key, text);
        }
    }

    /** Serializes a value to JSON, returning {@code "{}"} if serialization fails. */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    /**
     * Extracts the JSON object substring (from the first {@code &#123;} to the last
     * {@code &#125;}) so stray prose around the model's JSON is ignored. Returns the
     * trimmed input unchanged when no braces are found.
     */
    private String jsonOnly(String content) {
        String normalized = content == null ? "" : content.trim();
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        return start >= 0 && end > start ? normalized.substring(start, end + 1) : normalized;
    }

    /** Removes any trailing slashes from a URL; null becomes "". */
    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    /** Null-safe trim + lowercase (root locale) of a string. */
    private String lower(String value) {
        return string(value).toLowerCase(Locale.ROOT);
    }

    /** Null-safe conversion of any value to a trimmed string ("" for null). */
    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
