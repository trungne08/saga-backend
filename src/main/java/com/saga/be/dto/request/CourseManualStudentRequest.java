package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourseManualStudentRequest(
        @NotBlank @Size(max = 255) String studentCode,
        @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 255) String group,
        Boolean leader
) {
}
