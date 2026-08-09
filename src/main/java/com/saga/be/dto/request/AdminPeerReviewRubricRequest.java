package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AdminPeerReviewRubricRequest {

    @NotBlank
    private String criteriaName;

    @NotNull
    private BigDecimal weight;

    private String description;
}
