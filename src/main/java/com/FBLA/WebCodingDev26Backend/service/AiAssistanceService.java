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

@Service
public class AiAssistanceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiAssistanceService.class);
    private static final Pattern DATA_URL_PATTERN = Pattern.compile("^data:image/[^;]+;base64,", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "after", "an", "and", "at", "by", "for", "from", "i", "in", "is", "it", "lost", "near",
            "of", "on", "the", "to", "was", "with"
    );
    private static final List<String> COLORS = List.of(
            "Black", "White", "Red", "Blue", "Green", "Yellow", "Orange", "Purple", "Pink", "Brown", "Gray", "Silver", "Gold"
    );
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
    private static final List<String> BRANDS = List.of(
            "Apple", "AirPods", "Beats", "Samsung", "Sony", "Nike", "Adidas", "Hydro Flask", "JanSport",
            "Texas Instruments", "TI", "Dell", "HP", "Lenovo"
    );

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final boolean ollamaEnabled;
    private final String ollamaUrl;
    private final String ollamaModel;

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

    public Map<String, Object> suggestFoundItemFields(Map<String, Object> input) {
        Map<String, Object> safe = safeFoundItemInput(input);
        Map<String, Object> deterministic = deterministicFoundItemSuggestions(safe);
        Map<String, Object> ollama = askOllamaForFoundItem(safe);
        return mergeSuggestion(deterministic, ollama, "field suggestions");
    }

    public Map<String, Object> parseSearchQuery(Map<String, Object> input) {
        String query = string(input == null ? null : input.get("query"));
        Map<String, Object> deterministic = deterministicSearch(query);
        Map<String, Object> ollama = askOllamaForSearch(query);
        return mergeSuggestion(deterministic, ollama, "search interpretation");
    }

    private Map<String, Object> deterministicFoundItemSuggestions(Map<String, Object> safe) {
        String text = String.join(" ",
                string(safe.get("title")),
                string(safe.get("description")),
                string(safe.get("file_name"))
        );
        Map<String, Object> result = new LinkedHashMap<>();
        putIfPresent(result, "category", detectCategory(text));
        putIfPresent(result, "color", detectColor(text));
        putIfPresent(result, "brand", detectBrand(text));
        result.put("tags", tags(text, string(result.get("category")), string(result.get("color")), string(result.get("brand"))));
        result.put("explanation", "Deterministic fallback used public item text, filename, and visible photo metadata only.");
        result.put("source", "deterministic");
        result.put("used_ollama", false);
        result.put("editable", true);
        return result;
    }

    private Map<String, Object> deterministicSearch(String query) {
        Map<String, Object> result = new LinkedHashMap<>();
        putIfPresent(result, "category", detectCategory(query));
        putIfPresent(result, "color", detectColor(query));
        putIfPresent(result, "brand", detectBrand(query));
        putIfPresent(result, "location", detectLocation(query));
        result.put("keywords", tags(query, "", "", ""));
        result.put("date_hint", detectDateHint(query));
        result.put("explanation", "Search interpretation is deterministic: keywords, color, category, location, and date hints were parsed from the query text.");
        result.put("source", "deterministic");
        result.put("used_ollama", false);
        result.put("editable", true);
        return result;
    }

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

    private Map<String, Object> askOllamaForSearch(String query) {
        String prompt = """
                Parse this lost-and-found search query into editable filters. Never approve ownership or claims.
                Return JSON only: {"keywords":[],"category":"","color":"","brand":"","location":"","date_hint":"","explanation":""}
                Query: "%s"
                """.formatted(query == null ? "" : query);
        return callOllama(prompt, List.of());
    }

    private Map<String, Object> callOllama(String prompt, List<String> images) {
        if (!ollamaEnabled || ollamaUrl.isBlank()) {
            return Map.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ollamaModel);
        body.put("prompt", prompt);
        body.put("stream", false);
        body.put("format", "json");
        if (images != null && !images.isEmpty()) {
            body.put("images", images);
        }
        try {
            String raw = restClient.post()
                    .uri(URI.create(ollamaUrl + "/api/generate"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            String response = root.path("response").asText("");
            if (response.isBlank()) {
                return Map.of();
            }
            JsonNode parsed = objectMapper.readTree(jsonOnly(response));
            Map<String, Object> map = objectMapper.convertValue(parsed, new TypeReference<>() {});
            map.put("source", "ollama_assisted");
            map.put("used_ollama", true);
            return map;
        } catch (RestClientException | IllegalArgumentException | JsonProcessingException exception) {
            LOGGER.warn("Optional Ollama assistance unavailable; using deterministic fallback: {}", exception.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> mergeSuggestion(Map<String, Object> deterministic, Map<String, Object> ollama, String label) {
        if (ollama == null || ollama.isEmpty()) {
            return deterministic;
        }
        Map<String, Object> merged = new LinkedHashMap<>(deterministic);
        for (String key : List.of("category", "color", "brand", "location", "date_hint", "explanation")) {
            putIfPresent(merged, key, ollama.get(key));
        }
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

    private Map<String, Object> safeFoundItemInput(Map<String, Object> input) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (input == null) {
            return safe;
        }
        putIfPresent(safe, "title", input.get("title"));
        putIfPresent(safe, "description", input.get("description"));
        putIfPresent(safe, "file_name", input.get("file_name"));
        Object photos = input.get("photo_urls");
        if (photos instanceof List<?> list) {
            safe.put("photo_urls", list.stream().map(this::string).filter(value -> !value.isBlank()).limit(1).toList());
        }
        return safe;
    }

    private String detectCategory(String text) {
        String lowered = lower(text);
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(lowered::contains)) {
                return entry.getKey();
            }
        }
        return "";
    }

    private String detectColor(String text) {
        String lowered = lower(text);
        return COLORS.stream().filter(color -> lowered.contains(color.toLowerCase(Locale.ROOT))).findFirst().orElse("");
    }

    private String detectBrand(String text) {
        String lowered = lower(text);
        return BRANDS.stream().filter(brand -> lowered.contains(brand.toLowerCase(Locale.ROOT))).findFirst().orElse("");
    }

    private String detectLocation(String text) {
        String lowered = lower(text);
        for (Map.Entry<String, List<String>> entry : LOCATIONS.entrySet()) {
            if (entry.getValue().stream().anyMatch(lowered::contains)) {
                return entry.getKey();
            }
        }
        return "";
    }

    private String detectDateHint(String query) {
        String lowered = lower(query);
        if (lowered.contains("today")) return "today";
        if (lowered.contains("yesterday")) return "yesterday";
        if (lowered.contains("after game") || lowered.contains("after the game")) return "after game";
        if (lowered.contains("this week")) return "this week";
        return "";
    }

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

    private List<String> sanitizeList(List<?> raw) {
        return raw.stream().map(this::string).filter(value -> !value.isBlank()).limit(8).toList();
    }

    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        String text = string(value);
        if (!text.isBlank()) {
            payload.put(key, text);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String jsonOnly(String content) {
        String normalized = content == null ? "" : content.trim();
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        return start >= 0 && end > start ? normalized.substring(start, end + 1) : normalized;
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String lower(String value) {
        return string(value).toLowerCase(Locale.ROOT);
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
