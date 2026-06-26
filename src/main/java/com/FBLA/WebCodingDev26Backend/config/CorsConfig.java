package com.FBLA.WebCodingDev26Backend.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private final String frontendUrl;

    public CorsConfig(@Value("${app.frontend-url:}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Allow any local dev port (5173, 5174, 5180, …) so multiple frontend
        // instances can talk to this backend without editing this list each time.
        List<String> originPatterns = new ArrayList<>(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"
        ));
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            originPatterns.add(frontendUrl);
        }

        registry.addMapping("/api/**")
                .allowedOriginPatterns(originPatterns.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
