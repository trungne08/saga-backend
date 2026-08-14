package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectTypeCreateRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        String criteriaConfig
) {
}
