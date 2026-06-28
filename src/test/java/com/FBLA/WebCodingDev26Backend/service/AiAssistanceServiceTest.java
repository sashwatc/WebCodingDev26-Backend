package com.FBLA.WebCodingDev26Backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AiAssistanceService}'s deterministic (rule-based) fallback path.
 *
 * <p>The service under test is constructed with the Ollama LLM integration disabled
 * ({@code useOllama = false}), so all responses come from the local deterministic logic rather
 * than a network call. These tests assert the heuristic field extraction (category, color, tags,
 * location, date hints) and, critically, that private/sensitive inputs never leak into the
 * suggestion output. No mocks are needed since the service has no live collaborators in this mode.</p>
 */
class AiAssistanceServiceTest {
    // System under test: AI assistance service wired with Ollama disabled so the deterministic
    // fallback is exercised (the URL/model args are inert while disabled).
    private final AiAssistanceService service = new AiAssistanceService(new ObjectMapper(), false, "http://localhost:11434", "llava");

    /**
     * Scenario: request found-item field suggestions from a payload that also carries private
     * verification fields.
     * Arrange: supply a title/description plus private_verification_clues and proof_photo_url.
     * Act: call suggestFoundItemFields.
     * Assert: the result is marked deterministic (used_ollama=false), correctly infers category
     * "electronics" and color "Black", and derives tags including "airpods"/"black". The final
     * assertions prove the private clue text and proof URL are absent from the serialized output,
     * confirming sensitive data is never echoed back in public suggestions.
     */
    @Test
    void foundItemSuggestionsUseDeterministicFallbackWithoutPrivateFields() {
        Map<String, Object> result = service.suggestFoundItemFields(Map.of(
                "title", "Black AirPods case",
                "description", "Found near the gym after the game.",
                "private_verification_clues", List.of("initials inside case"),
                "proof_photo_url", "private-proof"
        ));

        assertThat(result.get("source")).isEqualTo("deterministic"); // came from rule-based fallback
        assertThat(result.get("used_ollama")).isEqualTo(false); // confirms LLM was not invoked
        assertThat(result.get("category")).isEqualTo("electronics"); // inferred from "AirPods"
        assertThat(result.get("color")).isEqualTo("Black"); // inferred from title
        assertThat(result.get("tags").toString()).contains("airpods", "black"); // derived tags
        assertThat(result.toString()).doesNotContain("initials inside case", "private-proof"); // no private leakage
    }

    /**
     * Scenario: parse a free-text search query into structured, user-editable filter fields.
     * Arrange: a natural-language query "black earbuds near gym after game".
     * Act: call parseSearchQuery.
     * Assert: the deterministic parser extracts category=electronics, color=Black,
     * location=Gymnasium, and a date_hint of "after game", and flags the result editable=true so
     * the user can adjust it. The final assertion proves no spurious "confidence" key is added on
     * the deterministic path (confidence is only meaningful for AI-generated parses). Passing proves
     * NL queries map to predictable, editable filters without an LLM.
     */
    @Test
    void searchSuggestionsParseNaturalLanguageIntoEditableFilters() {
        Map<String, Object> result = service.parseSearchQuery(Map.of("query", "black earbuds near gym after game"));

        assertThat(result.get("source")).isEqualTo("deterministic"); // rule-based parse
        assertThat(result.get("category")).isEqualTo("electronics"); // "earbuds" -> electronics
        assertThat(result.get("color")).isEqualTo("Black"); // color keyword extracted
        assertThat(result.get("location")).isEqualTo("Gymnasium"); // "gym" normalized to a zone
        assertThat(result.get("date_hint")).isEqualTo("after game"); // temporal phrase captured as a hint
        assertThat(result.get("editable")).isEqualTo(true); // user may refine the parsed filters
        assertThat(result).doesNotContainKey("confidence"); // no AI confidence on deterministic path
    }
}
