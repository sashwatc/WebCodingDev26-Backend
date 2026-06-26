package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.service.AiAssistanceService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-assistance")
public class AiAssistanceController {
    private final AiAssistanceService service;

    public AiAssistanceController(AiAssistanceService service) {
        this.service = service;
    }

    @PostMapping("/found-item")
    public Map<String, Object> suggestFoundItemFields(@RequestBody Map<String, Object> input) {
        return service.suggestFoundItemFields(input);
    }

    @PostMapping("/suggest-fields")
    public Map<String, Object> suggestFields(@RequestBody Map<String, Object> input) {
        return service.suggestFoundItemFields(input);
    }

    @PostMapping("/search")
    public Map<String, Object> parseSearchQuery(@RequestBody Map<String, Object> input) {
        return service.parseSearchQuery(input);
    }

    @PostMapping("/parse-search")
    public Map<String, Object> parseSearch(@RequestBody Map<String, Object> input) {
        return service.parseSearchQuery(input);
    }
}
