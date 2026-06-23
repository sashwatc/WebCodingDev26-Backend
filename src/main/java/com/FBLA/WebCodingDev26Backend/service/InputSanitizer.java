package com.FBLA.WebCodingDev26Backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class InputSanitizer {
    private static final Pattern SCRIPT_BLOCK = Pattern.compile("(?is)<\\s*(script|style)[^>]*>.*?<\\s*/\\s*\\1\\s*>");
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");

    public Map<String, Object> sanitizeMap(Map<String, Object> data) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (data == null) {
            return sanitized;
        }
        data.forEach((key, value) -> sanitized.put(key, sanitizeValue(value)));
        return sanitized;
    }

    public String sanitizeText(String value) {
        if (value == null) {
            return "";
        }
        String withoutScripts = SCRIPT_BLOCK.matcher(value).replaceAll("");
        String withoutTags = HTML_TAG.matcher(withoutScripts).replaceAll("");
        String withoutControls = CONTROL_CHARACTERS.matcher(withoutTags).replaceAll("");
        return withoutControls
                .replace("javascript:", "")
                .replace("JAVASCRIPT:", "")
                .trim();
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof String text) {
            return sanitizeText(text);
        }
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> nested = new LinkedHashMap<>();
            rawMap.forEach((key, nestedValue) -> nested.put(String.valueOf(key), sanitizeValue(nestedValue)));
            return nested;
        }
        if (value instanceof List<?> rawList) {
            List<Object> sanitized = new ArrayList<>();
            rawList.forEach(item -> sanitized.add(sanitizeValue(item)));
            return sanitized;
        }
        return value;
    }
}
