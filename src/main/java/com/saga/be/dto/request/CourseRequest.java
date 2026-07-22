package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.UUID;

@Data
public class CourseRequest {
    @NotBlank
    @Size(max = 255)
    private String courseCode;

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotNull
    private UUID subjectId;

    @NotNull
    private UUID classId;

    @NotNull
    private UUID semesterId;

    @NotNull
    private UUID instructorId;
}
