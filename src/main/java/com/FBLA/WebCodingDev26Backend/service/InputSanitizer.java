package com.FBLA.WebCodingDev26Backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Defensive input-sanitization helper used across the service layer to strip
 * potentially dangerous content from untrusted, client-supplied payloads before
 * they are mapped onto entities and persisted.
 *
 * <p>Primary concerns: neutralizing stored-XSS vectors (script/style blocks, HTML
 * tags, {@code javascript:} URIs) and removing disallowed control characters.
 * It operates structurally, recursing through nested maps and lists so every
 * string anywhere in a request body is cleaned.
 *
 * <p>Stateless and side-effect free; safe to share as a singleton Spring component.
 */
@Component
public class InputSanitizer {
    /** Matches an entire {@code <script>...</script>} or {@code <style>...</style>} block (case-insensitive, dot-matches-newline) so its contents are removed wholesale. */
    private static final Pattern SCRIPT_BLOCK = Pattern.compile("(?is)<\\s*(script|style)[^>]*>.*?<\\s*/\\s*\\1\\s*>");
    /** Matches any remaining HTML tag so all markup is stripped, leaving plain text. */
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<[^>]+>");
    /** Matches control characters except the allowed whitespace (carriage return, newline, tab). */
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");

    /**
     * Returns a sanitized copy of an entire request-body map.
     *
     * <p>Iteration order is preserved (LinkedHashMap). Each value is cleaned via
     * {@link #sanitizeValue(Object)}, recursing into nested maps/lists. Keys are
     * left untouched.
     *
     * @param data raw input map (may be {@code null})
     * @return a new map of sanitized values; an empty map when {@code data} is {@code null}
     */
    public Map<String, Object> sanitizeMap(Map<String, Object> data) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (data == null) {
            return sanitized;
        }
        // Clean every value while preserving original keys and insertion order.
        data.forEach((key, value) -> sanitized.put(key, sanitizeValue(value)));
        return sanitized;
    }

    /**
     * Cleans a single string value.
     *
     * <p>Sanitization pipeline, in order:
     * <ol>
     *   <li>remove whole {@code <script>}/{@code <style>} blocks (tag + body);</li>
     *   <li>strip any other HTML tags;</li>
     *   <li>remove disallowed control characters;</li>
     *   <li>neutralize {@code javascript:} scheme prefixes (both cases);</li>
     *   <li>trim surrounding whitespace.</li>
     * </ol>
     *
     * @param value raw text (may be {@code null})
     * @return the sanitized text, or an empty string when {@code value} is {@code null}
     */
    public String sanitizeText(String value) {
        if (value == null) {
            return "";
        }
        // 1. Drop script/style elements entirely (including their inner content).
        String withoutScripts = SCRIPT_BLOCK.matcher(value).replaceAll("");
        // 2. Strip remaining HTML markup.
        String withoutTags = HTML_TAG.matcher(withoutScripts).replaceAll("");
        // 3. Remove control characters (keeping CR/LF/TAB).
        String withoutControls = CONTROL_CHARACTERS.matcher(withoutTags).replaceAll("");
        // 4. Strip javascript: URI schemes, then 5. trim outer whitespace.
        return withoutControls
                .replace("javascript:", "")
                .replace("JAVASCRIPT:", "")
                .trim();
    }

    /**
     * Recursively sanitizes an arbitrary value based on its runtime type.
     *
     * <p>Strings are text-sanitized; maps and lists are walked element-by-element
     * (nested maps have their keys stringified); any other type is returned as-is
     * (numbers, booleans, etc. carry no injection risk).
     *
     * @param value the value to sanitize
     * @return the sanitized equivalent
     */
    private Object sanitizeValue(Object value) {
        // Plain string: run the text sanitizer.
        if (value instanceof String text) {
            return sanitizeText(text);
        }
        // Nested object: recurse into each entry, stringifying keys.
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> nested = new LinkedHashMap<>();
            rawMap.forEach((key, nestedValue) -> nested.put(String.valueOf(key), sanitizeValue(nestedValue)));
            return nested;
        }
        // Array/list: recurse into each element.
        if (value instanceof List<?> rawList) {
            List<Object> sanitized = new ArrayList<>();
            rawList.forEach(item -> sanitized.add(sanitizeValue(item)));
            return sanitized;
        }
        // Non-textual scalar: nothing to sanitize.
        return value;
    }
}
