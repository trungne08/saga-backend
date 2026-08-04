package com.saga.be.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class PeerReviewCriterionRequest {

    @NotNull
    private UUID rubricId;

    @NotNull
    @Min(0)
    @Max(5)
    private Integer starRating;
}
