package com.saga.be.integration.provider;

import java.util.List;

public record JiraCreateField(
        String key,
        String name,
        boolean required,
        String schemaType,
        String schemaItems,
        List<JiraCreateFieldAllowedValue> allowedValues
) {

    public JiraCreateField {
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }
}
