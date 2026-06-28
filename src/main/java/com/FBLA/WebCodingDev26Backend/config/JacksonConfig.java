package com.FBLA.WebCodingDev26Backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson JSON configuration for the backend.
 *
 * <p>Defines the application's primary {@link ObjectMapper} bean, which Spring
 * uses to serialize/deserialize JSON for the REST controllers. The key choice
 * here is snake_case property naming, so Java camelCase fields (e.g.
 * {@code foundItemId}) are exposed to the frontend as {@code found_item_id} and
 * vice versa — keeping the API contract consistent with the client.</p>
 */
@Configuration
public class JacksonConfig {
    /**
     * Builds the shared {@link ObjectMapper} used for all JSON (de)serialization.
     *
     * <p>Marked {@link Bean} so Spring registers it in the context and injects it
     * wherever an ObjectMapper is needed (including Spring MVC's HTTP message
     * converters). It maps Java camelCase property names to/from JSON snake_case
     * and auto-registers any Jackson modules found on the classpath (e.g.
     * JavaTime for date/time types).</p>
     *
     * @return a configured ObjectMapper instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                // Translate Java camelCase <-> JSON snake_case for the API contract.
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                // Discover and register Jackson modules on the classpath (e.g. JSR-310 time).
                .findAndAddModules()
                .build();
    }
}
