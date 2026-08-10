package com.saga.be.dto.request;

import jakarta.validation.constraints.Size;

public record CourseStudentTeamUpdateRequest(
        @Size(max = 255) String group,
        Boolean leader
) {
}
