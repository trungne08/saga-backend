package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClassRequest {
    @NotBlank
    @Size(max = 255)
    private String classCode;

    @NotBlank
    @Size(max = 255)
    private String name;
}
