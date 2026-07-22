package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubjectRequest {
    @NotBlank
    @Size(max = 255)
    private String subjectCode;

    @NotBlank
    @Size(max = 255)
    private String name;
}
