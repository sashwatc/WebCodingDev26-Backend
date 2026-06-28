package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.service.AiAssistanceService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI-assistance endpoints for the lost-and-found UI.
 *
 * <p>Serves the base route {@code /api/ai-assistance}. These endpoints are thin
 * pass-throughs to {@link AiAssistanceService}, which performs the actual AI
 * inference: (1) suggesting structured found-item fields from free-form input, and
 * (2) parsing a natural-language search query into structured search criteria.
 * No authorization is enforced here. Each endpoint is exposed under two path aliases
 * (e.g. {@code /found-item} and {@code /suggest-fields}) that invoke the same service
 * method, so both legacy and current frontend callers are supported.</p>
 */
@RestController // REST controller: handler return values are serialized to the response body
@RequestMapping("/api/ai-assistance") // base route for the AI helper endpoints
public class AiAssistanceController {
    // Performs the AI field-suggestion and search-parsing work.
    private final AiAssistanceService service;

    /** Constructor injection of the AI assistance service. */
    public AiAssistanceController(AiAssistanceService service) {
        this.service = service;
    }

    /**
     * POST /api/ai-assistance/found-item — suggest structured found-item fields from raw input.
     *
     * @param input request body: arbitrary key/value input (e.g. a description and/or image hints)
     *              forwarded to the AI service
     * @return a map of suggested field values produced by {@link AiAssistanceService#suggestFoundItemFields};
     *         200 OK. No authorization required.
     */
    @PostMapping("/found-item")
    public Map<String, Object> suggestFoundItemFields(@RequestBody Map<String, Object> input) {
        return service.suggestFoundItemFields(input);
    }

    /**
     * POST /api/ai-assistance/suggest-fields — alias of {@link #suggestFoundItemFields}.
     *
     * @param input request body forwarded unchanged to the AI service
     * @return the same suggested-fields map as {@code /found-item}; 200 OK. No authorization required.
     */
    @PostMapping("/suggest-fields")
    public Map<String, Object> suggestFields(@RequestBody Map<String, Object> input) {
        // Same behavior as suggestFoundItemFields — second path alias for the field-suggestion service.
        return service.suggestFoundItemFields(input);
    }

    /**
     * POST /api/ai-assistance/search — parse a natural-language search query into structured criteria.
     *
     * @param input request body: typically the user's free-text query
     * @return structured search parameters from {@link AiAssistanceService#parseSearchQuery}; 200 OK.
     *         No authorization required.
     */
    @PostMapping("/search")
    public Map<String, Object> parseSearchQuery(@RequestBody Map<String, Object> input) {
        return service.parseSearchQuery(input);
    }

    /**
     * POST /api/ai-assistance/parse-search — alias of {@link #parseSearchQuery}.
     *
     * @param input request body forwarded unchanged to the AI service
     * @return the same parsed search criteria as {@code /search}; 200 OK. No authorization required.
     */
    @PostMapping("/parse-search")
    public Map<String, Object> parseSearch(@RequestBody Map<String, Object> input) {
        // Same behavior as parseSearchQuery — second path alias for the search-parsing service.
        return service.parseSearchQuery(input);
    }
}
