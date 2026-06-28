package com.FBLA.WebCodingDev26Backend.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC CORS (Cross-Origin Resource Sharing) configuration for the backend's
 * REST API.
 *
 * <p>Browsers block cross-origin requests by default, so the React/Vite frontend
 * (served from a different origin/port than this backend) could not call the API
 * without an explicit CORS policy. By implementing {@link WebMvcConfigurer} and
 * being picked up as a {@link Configuration} bean, this class hooks into Spring
 * MVC and registers which origins, HTTP methods, and headers are permitted for
 * the {@code /api/**} endpoints exposed by the controllers.</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    // Optional production/explicit frontend origin (e.g. the deployed site URL),
    // injected from the app.frontend-url property; empty/blank when unset.
    private final String frontendUrl;

    /**
     * Constructs the CORS config, capturing the configured frontend URL.
     *
     * @param frontendUrl value of the {@code app.frontend-url} property; defaults
     *                    to an empty string (the {@code :} with nothing after it)
     *                    when the property is absent, meaning "no extra origin".
     */
    public CorsConfig(@Value("${app.frontend-url:}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    /**
     * Registers the CORS rules with Spring MVC's {@link CorsRegistry}. Called once
     * by the framework during startup.
     *
     * <p>Allows any localhost/127.0.0.1 dev port plus the optional configured
     * frontend URL to call the API, and permits the standard REST verbs and any
     * request header.</p>
     *
     * @param registry the registry Spring provides for declaring CORS mappings
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Allow any local dev port (5173, 5174, 5180, …) so multiple frontend
        // instances can talk to this backend without editing this list each time.
        // Base allow-list of origin patterns; the trailing :* wildcard matches any port.
        List<String> originPatterns = new ArrayList<>(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"
        ));
        // Append the explicitly configured frontend origin only when one was provided.
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            originPatterns.add(frontendUrl);
        }

        // Apply the policy to every API route under /api/**:
        registry.addMapping("/api/**")
                // allowedOriginPatterns (vs allowedOrigins) supports the :* wildcard.
                .allowedOriginPatterns(originPatterns.toArray(String[]::new))
                // Permit the REST verbs the API uses, plus OPTIONS for CORS preflight.
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                // Allow any request header (e.g. Content-Type, custom auth headers).
                .allowedHeaders("*");
    }
}
