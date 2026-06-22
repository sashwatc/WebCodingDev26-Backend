package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.MatchSuggestion;
import com.FBLA.WebCodingDev26Backend.service.MatchmakingService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
public class MatchmakingController {
    private final MatchmakingService service;

    public MatchmakingController(MatchmakingService service) {
        this.service = service;
    }

    @GetMapping("/lost-reports/{id}")
    public List<MatchSuggestion> getLostReportMatches(@PathVariable String id) {
        return service.getMatchesForLostReport(id);
    }

    @PostMapping("/lost-reports/{id}/refresh")
    public List<MatchSuggestion> refreshLostReportMatches(@PathVariable String id) {
        return service.refreshMatchesForLostReport(id);
    }

    @PostMapping("/found-items/{id}/refresh")
    public List<MatchSuggestion> refreshFoundItemMatches(@PathVariable String id) {
        return service.refreshMatchesForFoundItem(id);
    }
}
