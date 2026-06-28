package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.MatchSuggestion;
import com.FBLA.WebCodingDev26Backend.service.MatchmakingService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for the matchmaking engine that pairs lost reports with found items.
 *
 * <p>Base route: {@code /api/matches} ({@link RequestMapping}). Exposes one read endpoint to fetch
 * already-computed match suggestions for a lost report, plus two "refresh" endpoints that
 * recompute suggestions from either side of the pairing (lost report or found item). All scoring
 * and persistence of {@link MatchSuggestion}s is delegated to {@link MatchmakingService}.
 */
@RestController
@RequestMapping("/api/matches")
public class MatchmakingController {
    // Service that computes, refreshes and stores match suggestions between lost and found records.
    private final MatchmakingService service;

    /** Constructor injection of the matchmaking service. */
    public MatchmakingController(MatchmakingService service) {
        this.service = service;
    }

    /**
     * GET {@code /api/matches/lost-reports/{id}} — list current match suggestions for a lost report.
     *
     * @param id path variable: the lost report id.
     * @return 200 OK with the lost report's existing {@link MatchSuggestion}s (without recomputing).
     */
    @GetMapping("/lost-reports/{id}")
    public List<MatchSuggestion> getLostReportMatches(@PathVariable String id) {
        return service.getMatchesForLostReport(id);
    }

    /**
     * POST {@code /api/matches/lost-reports/{id}/refresh} — recompute suggestions for a lost report.
     *
     * @param id path variable: the lost report id to re-evaluate against found items.
     * @return 200 OK with the freshly recomputed list of {@link MatchSuggestion}s.
     */
    @PostMapping("/lost-reports/{id}/refresh")
    public List<MatchSuggestion> refreshLostReportMatches(@PathVariable String id) {
        return service.refreshMatchesForLostReport(id);
    }

    /**
     * POST {@code /api/matches/found-items/{id}/refresh} — recompute suggestions for a found item.
     *
     * @param id path variable: the found item id to re-evaluate against lost reports.
     * @return 200 OK with the freshly recomputed list of {@link MatchSuggestion}s.
     */
    @PostMapping("/found-items/{id}/refresh")
    public List<MatchSuggestion> refreshFoundItemMatches(@PathVariable String id) {
        return service.refreshMatchesForFoundItem(id);
    }
}
