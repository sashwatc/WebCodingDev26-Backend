package com.FBLA.WebCodingDev26Backend.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.beans.FeatureDescriptor;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

@Component
public class PatchMapper {
    private final ObjectMapper objectMapper;

    public PatchMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public <T> T convert(Map<String, Object> data, Class<T> type) {
        return objectMapper.convertValue(data == null ? Map.of() : data, type);
    }

    public void copyNonNull(Object source, Object target, String... ignoredProperties) {
        String[] nullProperties = getNullPropertyNames(source);
        String[] ignored = Stream.concat(Stream.of(nullProperties), Stream.of(ignoredProperties)).distinct().toArray(String[]::new);
        BeanUtils.copyProperties(source, target, ignored);
    }

    public void copyPresent(Map<String, Object> data, Object source, Object target, String... ignoredProperties) {
        Set<String> ignored = Set.copyOf(Arrays.asList(ignoredProperties));
        BeanWrapper sourceWrapper = new BeanWrapperImpl(source);
        BeanWrapper targetWrapper = new BeanWrapperImpl(target);

        (data == null ? Map.<String, Object>of() : data).keySet().stream()
                .map(this::toJavaProperty)
                .filter(property -> !ignored.contains(property))
                .filter(property -> sourceWrapper.isReadableProperty(property) && targetWrapper.isWritableProperty(property))
                .forEach(property -> targetWrapper.setPropertyValue(property, sourceWrapper.getPropertyValue(property)));
    }

    private String[] getNullPropertyNames(Object source) {
        BeanWrapper wrappedSource = new BeanWrapperImpl(source);
        return Stream.of(wrappedSource.getPropertyDescriptors())
                .map(FeatureDescriptor::getName)
                .filter(propertyName -> wrappedSource.getPropertyValue(propertyName) == null)
                .toArray(String[]::new);
    }

    private String toJavaProperty(String key) {
        String value = key == null ? "" : key.trim();
        StringBuilder property = new StringBuilder();
        boolean uppercaseNext = false;

        for (char character : value.toCharArray()) {
            if (character == '_') {
                uppercaseNext = true;
            } else if (uppercaseNext) {
                property.append(Character.toUpperCase(character));
                uppercaseNext = false;
            } else {
                property.append(character);
            }
        }

        return property.toString();
    }
}
