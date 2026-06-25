package com.FBLA.WebCodingDev26Backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiAssistanceServiceTest {
    private final AiAssistanceService service = new AiAssistanceService(new ObjectMapper(), false, "http://localhost:11434", "llava");

    @Test
    void foundItemSuggestionsUseDeterministicFallbackWithoutPrivateFields() {
        Map<String, Object> result = service.suggestFoundItemFields(Map.of(
                "title", "Black AirPods case",
                "description", "Found near the gym after the game.",
                "private_verification_clues", List.of("initials inside case"),
                "proof_photo_url", "private-proof"
        ));

        assertThat(result.get("source")).isEqualTo("deterministic");
        assertThat(result.get("used_ollama")).isEqualTo(false);
        assertThat(result.get("category")).isEqualTo("electronics");
        assertThat(result.get("color")).isEqualTo("Black");
        assertThat(result.get("tags").toString()).contains("airpods", "black");
        assertThat(result.toString()).doesNotContain("initials inside case", "private-proof");
    }

    @Test
    void searchSuggestionsParseNaturalLanguageIntoEditableFilters() {
        Map<String, Object> result = service.parseSearchQuery(Map.of("query", "black earbuds near gym after game"));

        assertThat(result.get("source")).isEqualTo("deterministic");
        assertThat(result.get("category")).isEqualTo("electronics");
        assertThat(result.get("color")).isEqualTo("Black");
        assertThat(result.get("location")).isEqualTo("Gymnasium");
        assertThat(result.get("date_hint")).isEqualTo("after game");
        assertThat(result.get("editable")).isEqualTo(true);
        assertThat(result).doesNotContainKey("confidence");
    }
}
