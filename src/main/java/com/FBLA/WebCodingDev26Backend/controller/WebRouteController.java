package com.FBLA.WebCodingDev26Backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * MVC controller that serves the single-page application's HTML shell for client-side routes.
 *
 * <p>Unlike the {@code @RestController}s in this package, this is a plain {@code @Controller} that
 * returns HTML view routes, NOT JSON. Its return value is interpreted as a view/forward instruction
 * rather than serialized to the response body.
 *
 * <p>It maps each known front-end (SPA) path to a server-side forward to the static
 * {@code /index.html}, so deep links and browser refreshes on client-routed URLs still load the app
 * (which then handles routing in the browser). Because it only forwards, there are no request inputs,
 * no authorization checks here, and no error handling beyond the standard servlet forward.
 */
@Controller // MVC controller: returns view/forward strings (HTML), not JSON
public class WebRouteController {
    /**
     * GET for each listed SPA route (e.g. {@code /}, {@code /browse}, {@code /admin}, {@code /login},
     * {@code /auth/callback}, etc.).
     *
     * <p>Takes no parameters and performs no logic; it server-side forwards the request to
     * {@code /index.html} (the SPA shell). The browser URL is preserved (forward, not redirect), so
     * the front-end router renders the correct view. Responds with the HTML of {@code index.html}
     * (HTTP 200).
     *
     * @return the forward instruction {@code "forward:/index.html"}.
     */
    @GetMapping({
            "/",
            "/report-lost",
            "/report-found",
            "/browse",
            "/claim",
            "/admin",
            "/chat",
            "/admin/chat",
            "/login",
            "/signup",
            "/verify-email",
            "/auth/callback",
            "/sources"
    })
    public String app() {
        return "forward:/index.html"; // internal forward (URL unchanged) to the SPA entry page
    }
}
