package com.saga.be.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(List<String> labels) {
        try {
            return OBJECT_MAPPER.writeValueAsString(
                    labels == null ? List.of() : List.copyOf(labels)
            );
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Task labels cannot be serialized", exception);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String labelsJson) {
        if (labelsJson == null || labelsJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> labels = OBJECT_MAPPER.readValue(labelsJson, STRING_LIST);
            return labels == null ? List.of() : List.copyOf(labels);
        } catch (JacksonException | NullPointerException exception) {
            throw new IllegalArgumentException("Task labels JSON is invalid", exception);
        }
    }
}
