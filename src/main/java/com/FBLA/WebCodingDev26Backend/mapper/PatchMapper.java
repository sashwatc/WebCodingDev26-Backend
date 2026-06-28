package com.FBLA.WebCodingDev26Backend.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

/**
 * Helper for applying PARTIAL updates (HTTP PATCH semantics) to entity/DTO beans.
 *
 * <p>Provides utilities to (a) turn a raw JSON-ish map into a typed object, and to
 * (b) copy only the fields the caller actually supplied onto an existing target object,
 * leaving every other field untouched. This avoids the classic PUT problem where omitted
 * fields would be overwritten with null.</p>
 */
@Component
public class PatchMapper {
    private final ObjectMapper objectMapper;

    // Use a private copy of the shared Jackson ObjectMapper (so config changes don't leak globally),
    // configured to ignore unknown properties instead of throwing when the input map has extra keys.
    public PatchMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // Deserializes a property map into an instance of the requested type via Jackson.
    // A null map is treated as an empty map, so the result is a default/empty instance of T.
    public <T> T convert(Map<String, Object> data, Class<T> type) {
        return objectMapper.convertValue(data == null ? Map.of() : data, type);
    }

    // Copies properties from source to target but SKIPS any source property that is null,
    // plus any caller-specified ignoredProperties. Useful when "null" means "field not provided".
    public void copyNonNull(Object source, Object target, String... ignoredProperties) {
        // Determine which source properties are currently null.
        String[] nullProperties = getNullPropertyNames(source);
        // Build the exclusion list = null properties + explicitly ignored properties (de-duplicated).
        String[] ignored = Stream.concat(Stream.of(nullProperties), Stream.of(ignoredProperties)).distinct().toArray(String[]::new);
        // Spring copies all remaining (non-ignored, non-null) properties from source onto target.
        BeanUtils.copyProperties(source, target, ignored);
    }

    // Copies only the properties whose keys are PRESENT in the raw 'data' map (true PATCH semantics):
    // even if a supplied value is null, it is applied; fields absent from 'data' are left untouched on target.
    public void copyPresent(Map<String, Object> data, Object source, Object target, String... ignoredProperties) {
        // Properties the caller wants excluded from the copy.
        Set<String> ignored = Set.copyOf(Arrays.asList(ignoredProperties));
        // BeanWrappers give reflective read/write access to the source (already-parsed values) and target beans.
        BeanWrapper sourceWrapper = new BeanWrapperImpl(source);
        BeanWrapper targetWrapper = new BeanWrapperImpl(target);

        // For each key the client actually sent (null map -> empty):
        (data == null ? Map.<String, Object>of() : data).keySet().stream()
                .map(this::toJavaProperty)                                                                  // normalize key to a Java property name (snake_case -> camelCase)
                .filter(property -> !ignored.contains(property))                                            // drop ignored properties
                .filter(property -> sourceWrapper.isReadableProperty(property) && targetWrapper.isWritableProperty(property)) // only properties readable on source and writable on target
                .forEach(property -> targetWrapper.setPropertyValue(property, sourceWrapper.getPropertyValue(property)));     // copy the value across
    }

    // Reflectively lists the names of all bean properties on 'source' whose current value is null.
    private String[] getNullPropertyNames(Object source) {
        BeanWrapper wrappedSource = new BeanWrapperImpl(source);
        return Stream.of(wrappedSource.getPropertyDescriptors())   // every declared property descriptor
                .map(descriptor -> descriptor.getName())           // -> property name
                .filter(propertyName -> wrappedSource.getPropertyValue(propertyName) == null) // keep only the null-valued ones
                .toArray(String[]::new);
    }

    // Converts an incoming JSON key into a Java/camelCase property name by stripping underscores
    // and uppercasing the character that follows each underscore (e.g. "found_item_id" -> "foundItemId").
    private String toJavaProperty(String key) {
        // Null key -> empty string; trim surrounding whitespace.
        String value = key == null ? "" : key.trim();
        StringBuilder property = new StringBuilder();
        boolean uppercaseNext = false; // flag: the next non-underscore char should be capitalized

        for (char character : value.toCharArray()) {
            if (character == '_') {
                uppercaseNext = true;              // underscore is dropped; mark next char to capitalize
            } else if (uppercaseNext) {
                property.append(Character.toUpperCase(character)); // capitalize char following an underscore
                uppercaseNext = false;
            } else {
                property.append(character);        // copy character as-is
            }
        }

        return property.toString();
    }
}
