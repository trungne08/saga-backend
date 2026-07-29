package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamProjectRequest(
        @NotBlank @Size(max = 255) String name
) {
}
