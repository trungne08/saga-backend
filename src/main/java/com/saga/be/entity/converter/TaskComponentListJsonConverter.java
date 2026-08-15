package com.saga.be.entity.converter;

import com.saga.be.entity.value.TaskComponentSnapshot;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Converter
public class TaskComponentListJsonConverter implements AttributeConverter<List<TaskComponentSnapshot>, String> {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<TaskComponentSnapshot>> COMPONENTS = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(List<TaskComponentSnapshot> components) {
        try {
            return OBJECT_MAPPER.writeValueAsString(components == null ? List.of() : List.copyOf(components));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Task components cannot be serialized", exception);
        }
    }

    @Override
    public List<TaskComponentSnapshot> convertToEntityAttribute(String componentsJson) {
        if (componentsJson == null || componentsJson.isBlank()) {
            return List.of();
        }
        try {
            List<TaskComponentSnapshot> components = OBJECT_MAPPER.readValue(componentsJson, COMPONENTS);
            return components == null ? List.of() : List.copyOf(components);
        } catch (JacksonException | NullPointerException exception) {
            throw new IllegalArgumentException("Task components JSON is invalid", exception);
        }
    }
}
